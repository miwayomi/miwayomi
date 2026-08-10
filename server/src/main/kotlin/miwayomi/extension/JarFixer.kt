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

    fun fixStackmapFrames(jar: File) {
        val appLoader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader

        val loader = java.net.URLClassLoader(arrayOf(jar.toURI().toURL()), appLoader)
        try {
            val classes = LinkedHashMap<String, ClassNode>()
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
                        } catch (_: Throwable) {
                            others.add(entry.name to data)
                        }
                    } else {
                        others.add(entry.name to data)
                    }
                }
            }

            classes.values.forEach { cn ->
                fixWrongInitOwner(cn, needInit, classes)
                fixConstructorSuperCall(cn, needInit)
                fixPhantomNew(cn, needInit)
                fixWrongFieldNew(cn, needInit, classes)
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

            JarOutputStream(FileOutputStream(jar)).use { jos ->
                classes.forEach { (name, cn) ->
                    val bytes = try {
                        val cw = object : ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                            override fun getCommonSuperClass(t1: String, t2: String): String = try {
                                val c1 = Class.forName(t1.replace('/', '.'), false, loader)
                                val c2 = Class.forName(t2.replace('/', '.'), false, loader)
                                when {
                                    c1.isAssignableFrom(c2) -> t1
                                    c2.isAssignableFrom(c1) -> t2
                                    else -> {
                                        var sup: Class<*>? = c1
                                        while (sup != null && !sup.isAssignableFrom(c2)) {
                                            sup = sup.superclass
                                        }
                                        sup?.name?.replace('.', '/') ?: "java/lang/Object"
                                    }
                                }
                            } catch (_: Throwable) {
                                "java/lang/Object"
                            }
                        }
                        cn.accept(cw)
                        cw.toByteArray()
                    } catch (_: Throwable) {
                        val cw = ClassWriter(0)
                        cn.accept(cw)
                        cw.toByteArray()
                    }
                    jos.putNextEntry(JarEntry(name))
                    jos.write(bytes)
                    jos.closeEntry()
                }
                others.forEach { (name, data) ->
                    jos.putNextEntry(JarEntry(name))
                    jos.write(data)
                    jos.closeEntry()
                }
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
                                val real = realTypeOf(classes, t, n.owner)
                                if (real != null) {
                                    newInsn.desc = real
                                    n.owner = real
                                    needInit.add(real to n.desc)
                                }
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
            var i = 0
            while (i < insns.size()) {
                val n = insns[i]
                if (n is MethodInsnNode && n.opcode == Opcodes.INVOKESPECIAL && n.name == "<init>") {
                    if (n.owner != cn.name && n.owner != superName) {
                        n.owner = superName
                        needInit.add(superName to n.desc)
                    }
                    break
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
                                // only touch bytecode that is already invalid (the value is not assignable to the field/use)
                                val fix = !assignable && (
                                    targetInJar ||
                                    isSubclassOf(classes, target, n.desc) ||
                                    newInJar
                                )
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
