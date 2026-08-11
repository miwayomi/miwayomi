package miwayomi.extension

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LineNumberNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

object JarFixer {

    private const val FIX_MARKER = "META-INF/miwayomi-jarfixed"

    /** True if [jar] was already processed by [fixStackmapFrames]. */
    fun isFixed(jar: File): Boolean = try {
        JarFile(jar).use { jf -> jf.getJarEntry(FIX_MARKER) != null }
    } catch (e: Exception) {
        false
    }

    fun fixStackmapFrames(jar: File) {
        val appLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader

        @Suppress("UNUSED_VARIABLE")
        val loader = java.net.URLClassLoader(arrayOf(jar.toURI().toURL()), appLoader)
        try {
            val classes = LinkedHashMap<String, ClassNode>()
            val originalBytes = HashMap<String, ByteArray>()
            val others = mutableListOf<Pair<String, ByteArray>>()
            val needInit = mutableSetOf<Pair<String, String>>()
            JarFile(jar).use { jf ->
                val e = jf.entries()
                while (e.hasMoreElements()) {
                    val entry = e.nextElement()
                    if (entry.isDirectory) continue
                    val data = jf.getInputStream(entry).readBytes()
                    if (entry.name.endsWith(".class")) {
                        try {
                            val cn = ClassNode()
                            ClassReader(data).accept(cn, 0)
                            classes[entry.name] = cn
                            originalBytes[entry.name] = data
                        } catch (_: Throwable) {
                            others.add(entry.name to data)
                        }
                    } else {
                        others.add(entry.name to data)
                    }
                }
            }

            // Snapshot each class's plain serialization before any fix, so we can
            // tell exactly which classes the fixers changed.
            val plainBefore = HashMap<String, ByteArray?>()
            classes.forEach { (name, cn) ->
                try {
                    plainBefore[name] = ClassWriter(0).apply { cn.accept(this) }.toByteArray()
                } catch (_: Throwable) {
                    plainBefore[name] = null
                }
            }

            classes.values.forEach { cn ->
                // Only fix the `invokespecial <init>` owner of `new T; dup; ...;
                // invokespecial <init>` constructions. This is the corruption dex2jar
                // introduces, and it is the only rewrite that is safe for every jar:
                // a clean jar has no such mismatch (the JVM verifier requires the
                // <init> owner to be the constructed type), so it is left untouched.
                // The other fixers (constructor super-call, phantom `new`, field-new
                // inference) proved too aggressive: they rewrite valid bytecode in
                // some extensions and break them with VerifyError.
                fixWrongInitOwner(cn, needInit, classes)
            }

            var changed = true
            while (changed) {
                changed = false
                needInit.toList().forEach { (internal, desc) ->
                    val cn = classes[internal + ".class"] ?: return@forEach
                    if (cn.access and Opcodes.ACC_INTERFACE != 0) return@forEach
                    val has = cn.methods.any { it.name == "<init>" && it.desc == desc }
                    if (!has) {
                        val m = MethodNode(Opcodes.ACC_PUBLIC, "<init>", desc, null, null)
                        m.instructions.add(VarInsnNode(Opcodes.ALOAD, 0))
                        var slot = 1
                        for (t in Type.getArgumentTypes(desc)) {
                            when (t.sort) {
                                Type.LONG -> { m.instructions.add(VarInsnNode(Opcodes.LLOAD, slot)); slot += 2 }
                                Type.DOUBLE -> { m.instructions.add(VarInsnNode(Opcodes.DLOAD, slot)); slot += 2 }
                                Type.FLOAT -> { m.instructions.add(VarInsnNode(Opcodes.FLOAD, slot)); slot++ }
                                Type.INT, Type.SHORT, Type.BYTE, Type.CHAR, Type.BOOLEAN -> { m.instructions.add(VarInsnNode(Opcodes.ILOAD, slot)); slot++ }
                                else -> { m.instructions.add(VarInsnNode(Opcodes.ALOAD, slot)); slot++ }
                            }
                        }
                        m.instructions.add(MethodInsnNode(Opcodes.INVOKESPECIAL, cn.superName, "<init>", desc, false))
                        m.instructions.add(InsnNode(Opcodes.RETURN))
                        cn.methods.add(m)
                        if (classes.containsKey(cn.superName + ".class")) {
                            needInit.add(cn.superName to desc)
                        }
                        changed = true
                    }
                }
            }

            // Only classes the fixers actually changed need new stack map frames.
            // Recomputing frames for untouched classes can break them (VerifyError),
            // so keep their original bytes.
            val dirty = HashSet<String>()
            classes.forEach { (name, cn) ->
                val before = plainBefore[name]
                if (before != null) {
                    val after = try {
                        ClassWriter(0).apply { cn.accept(this) }.toByteArray()
                    } catch (_: Throwable) {
                        null
                    }
                    if (after == null || !before.contentEquals(after)) {
                        dirty.add(name)
                    }
                }
            }

            val tmp = File(jar.parentFile, jar.name + ".tmp")
            try {
                JarOutputStream(FileOutputStream(tmp)).use { jos ->
                    // Mark the jar as fixed so this only runs once per extension.
                    jos.putNextEntry(JarEntry(FIX_MARKER))
                    jos.write("fixed\n".toByteArray())
                    jos.closeEntry()
                    classes.forEach { (name, cn) ->
                        val bytes = if (!dirty.contains(name)) {
                            // Unchanged class: keep the original bytes untouched.
                            originalBytes[name]
                        } else {
                            // The fixer only changes the <init> owner of `new T; dup; ...`
                            // constructions. Stack map frames store those receivers as
                            // uninitialized offsets (not the owner's name), so they stay
                            // valid: preserve the original frames (ClassWriter(0)) instead
                            // of recomputing them. COMPUTE_FRAMES resolves types through
                            // the classloader and can emit wrong frames for otherwise-fine
                            // extensions (VerifyError on load).
                            ClassWriter(0).apply { cn.accept(this) }.toByteArray()
                        }
                        if (bytes != null) {
                            jos.putNextEntry(JarEntry(name))
                            jos.write(bytes)
                            jos.closeEntry()
                        }
                    }
                    others.forEach { (name, data) ->
                        jos.putNextEntry(JarEntry(name))
                        jos.write(data)
                        jos.closeEntry()
                    }
                }
                // Replace the original jar only after the fixed copy was written
                // successfully, so a failure never corrupts the extension jar.
                try {
                    java.nio.file.Files.move(
                        tmp.toPath(), jar.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: Exception) {
                    java.nio.file.Files.move(
                        tmp.toPath(), jar.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        } catch (e: Exception) {
            System.err.println("fixStackmapFrames error in $jar: $e")
        }
    }

    private fun fixWrongInitOwner(cn: ClassNode, needInit: MutableSet<Pair<String, String>>, classes: Map<String, ClassNode>) {
        cn.methods.forEach { m ->
            val insns = m.instructions
            val pending = java.util.ArrayDeque<TypeInsnNode>()
            var pendingDup: TypeInsnNode? = null
            var i = 0
            while (i < insns.size()) {
                val n = insns[i]
                when {
                    n is TypeInsnNode && n.opcode == Opcodes.NEW -> pendingDup = n
                    n is InsnNode && n.opcode == Opcodes.DUP && pendingDup != null -> {
                        pending.push(pendingDup)
                        pendingDup = null
                    }
                    n is MethodInsnNode && n.opcode == Opcodes.INVOKESPECIAL && n.name == "<init>" -> {
                        if (!pending.isEmpty()) {
                            val newInsn = pending.pop()
                            val t = newInsn.desc
                            if (n.owner != t) {
                                // The receiver of an `invokespecial <init>` that follows a
                                // `new T; dup` is an object of type T, so T must own the
                                // <init> (the JVM verifier enforces this). dex2jar sometimes
                                // rewrites the owner to an unrelated class (e.g. ArrayList ->
                                // Filter.Select, Pair -> Filter subclass); the constructed
                                // type is always the correct owner.
                                n.owner = t
                                needInit.add(t to n.desc)
                            }
                        }
                    }
                    else -> {}
                }
                i++
            }
        }
    }

    private fun fixConstructorSuperCall(cn: ClassNode, needInit: MutableSet<Pair<String, String>>) {
        val superName = cn.superName
        cn.methods.filter { it.name == "<init>" }.forEach { m ->
            val insns = m.instructions
            // Track `new T; dup` constructions so we don't mistake a constructor
            // call on a freshly allocated object for the `this(...)`/`super(...)` call.
            val pending = java.util.ArrayDeque<TypeInsnNode>()
            var pendingDup: TypeInsnNode? = null
            var i = 0
            while (i < insns.size()) {
                val n = insns[i]
                when {
                    n is TypeInsnNode && n.opcode == Opcodes.NEW -> pendingDup = n
                    n is InsnNode && n.opcode == Opcodes.DUP && pendingDup != null -> {
                        pending.push(pendingDup)
                        pendingDup = null
                    }
                    n is MethodInsnNode && n.opcode == Opcodes.INVOKESPECIAL && n.name == "<init>" -> {
                        if (pending.isEmpty()) {
                            // Real `this(...)`/`super(...)` call: must point at this class
                            // or its superclass.
                            if (n.owner != cn.name && n.owner != superName) {
                                n.owner = superName
                                needInit.add(superName to n.desc)
                            }
                            break
                        }
                        pending.pop()
                    }
                }
                i++
            }
        }
    }

    private fun fixPhantomNew(cn: ClassNode, needInit: MutableSet<Pair<String, String>>) {
        cn.methods.forEach { m ->
            val insns = m.instructions
            var i = 0
            while (i < insns.size()) {
                val insn = insns[i]
                if (insn is TypeInsnNode && insn.opcode == Opcodes.NEW && insn.desc == "java/lang/Object") {
                    val dup = if (i + 1 < insns.size()) insns[i + 1] else null
                    val init = if (i + 2 < insns.size()) insns[i + 2] else null
                    if (dup is InsnNode && dup.opcode == Opcodes.DUP &&
                        init is MethodInsnNode && init.name == "<init>"
                    ) {
                        val use = if (i + 3 < insns.size()) insns[i + 3] else null
                        val target = when {
                            use is FieldInsnNode && (use.opcode == Opcodes.PUTFIELD || use.opcode == Opcodes.PUTSTATIC) ->
                                objTypeOf(use.desc)
                            use is VarInsnNode && use.opcode == Opcodes.ASTORE ->
                                findStoreType(insns, use.`var`, i + 4)
                            else -> null
                        }
                        if (target != null && target != "java/lang/Object") {
                            insn.desc = target
                            init.owner = target
                            needInit.add(target to init.desc)
                            i += 3
                        }
                    }
                }
                i++
            }
        }
    }

    private fun fixWrongFieldNew(cn: ClassNode, needInit: MutableSet<Pair<String, String>>, classes: Map<String, ClassNode>) {
        cn.methods.forEach { m ->
            val insns = m.instructions
            var i = 0
            while (i < insns.size()) {
                val n = insns[i]
                if (n is TypeInsnNode && n.opcode == Opcodes.NEW && n.desc != "java/lang/Object") {
                    var k = i + 1
                    while (k < insns.size() && insns[k] !is InsnNode) k++
                    if (k >= insns.size()) { i++; continue }
                    val dup = insns[k]
                    if (dup is InsnNode && dup.opcode == Opcodes.DUP) {
                        // locate the <init> of the constructor (skipping the arguments)
                        var j = k + 1
                        var init: MethodInsnNode? = null
                        var stop = false
                        while (j < insns.size() && !stop) {
                            val u = insns[j]
                            when (u) {
                                is MethodInsnNode -> if (u.opcode == Opcodes.INVOKESPECIAL && u.name == "<init>") { init = u; stop = true } else stop = true
                                is TypeInsnNode -> if (u.opcode == Opcodes.NEW) stop = true
                                is FieldInsnNode -> stop = true
                                else -> {}
                            }
                            if (!stop) j++
                        }
                        if (init != null) {
                            var target: String? = null
                            var consumed = j + 1
                            if (init.owner != n.desc) {
                                // new/init mismatch: the real type is the one defined inside the jar
                                target = realTypeOf(classes, n.desc, init.owner)
                            }
                            if (target == null || target == n.desc) {
                                // scan the consumer of the created object
                                var c = j + 1
                                while (c < insns.size()) {
                                    val u = insns[c]
                                    if (u is FieldInsnNode && (u.opcode == Opcodes.PUTFIELD || u.opcode == Opcodes.PUTSTATIC)) { target = objTypeOf(u.desc); consumed = c + 1; break }
                                    if (u is TypeInsnNode && u.opcode == Opcodes.CHECKCAST) { target = u.desc; consumed = c + 1; break }
                                    if (u is VarInsnNode && u.opcode == Opcodes.ASTORE) { target = findStoreType(insns, u.`var`, c + 1); consumed = c + 1; break }
                                    if (u is MethodInsnNode) break
                                    if (u is InsnNode && u.opcode == Opcodes.POP) break
                                    c++
                                }
                            }
                            if (target != null && target != n.desc) {
                                val targetInJar = classes.containsKey(target + ".class")
                                val newInJar = classes.containsKey(n.desc + ".class")
                                val assignable = isSubclassOf(classes, n.desc, target)
                                // Only rewrite `new` of classes defined INSIDE the jar:
                                // dex2jar mangles the names of in-jar classes. Never rewrite
                                // `new` of an external class (ArrayList, Pair, String, ...) —
                                // those are valid constructions, and rewriting them (e.g. to
                                // a Filter subclass) breaks the extension with VerifyError or
                                // InstantiationError. The `new`-vs-<init> owner mismatch is
                                // handled by fixWrongInitOwner instead.
                                val fix = !assignable && (
                                    targetInJar ||
                                    isSubclassOf(classes, target, n.desc)
                                ) && newInJar
                                if (fix) {
                                    n.desc = target
                                    init.owner = target
                                    needInit.add(target to init.desc)
                                    i = consumed
                                }
                            }
                        }
                    }
                }
                i++
            }
        }
    }

    private fun realTypeOf(classes: Map<String, ClassNode>, a: String, b: String): String? {
        val aIn = classes.containsKey(a + ".class")
        val bIn = classes.containsKey(b + ".class")
        return when {
            aIn && !bIn -> a
            bIn && !aIn -> b
            isSubclassOf(classes, b, a) -> b
            isSubclassOf(classes, a, b) -> a
            else -> null
        }
    }

    private fun isSubclassOf(classes: Map<String, ClassNode>, sub: String, sup: String): Boolean {
        if (sub == sup) return true
        var cur: String? = sub
        var depth = 0
        while (cur != null && depth < 64) {
            if (cur == sup) return true
            val node = classes[cur + ".class"] ?: return false
            if (node.interfaces.contains(sup)) return true
            cur = node.superName
            depth++
        }
        return false
    }

    private fun objTypeOf(desc: String): String? =
        if (desc.length > 2 && desc.startsWith("L") && desc.endsWith(";")) desc.substring(1, desc.length - 1) else null

    private fun findStoreType(insns: InsnList, slot: Int, start: Int): String? {
        var j = start
        while (j < insns.size()) {
            val n = insns[j]
            if (n is VarInsnNode && n.opcode == Opcodes.ALOAD && n.`var` == slot) {
                var k = j + 1
                while (k < insns.size()) {
                    val u = insns[k]
                    when (u) {
                        is FieldInsnNode -> {
                            if (u.opcode == Opcodes.PUTFIELD || u.opcode == Opcodes.PUTSTATIC) return u.owner
                            k++
                        }
                        is MethodInsnNode -> return null
                        is TypeInsnNode -> {
                            if (u.opcode == Opcodes.CHECKCAST) return u.desc
                            k++
                        }
                        is VarInsnNode -> k++
                        is LdcInsnNode -> k++
                        is InsnNode -> k++
                        is LabelNode, is LineNumberNode, is FrameNode -> k++
                        else -> return null
                    }
                }
            }
            j++
        }
        return null
    }
}
