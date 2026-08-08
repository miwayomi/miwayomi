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
                fixWrongInitOwner(cn, needInit)
                fixConstructorSuperCall(cn, needInit)
                fixPhantomNew(cn, needInit)
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
                                Type.LONG, Type.DOUBLE -> { m.instructions.add(VarInsnNode(if (t.sort == Type.LONG) Opcodes.LLOAD else Opcodes.DLOAD, slot)); slot += 2 }
                                Type.FLOAT -> { m.instructions.add(VarInsnNode(Opcodes.FLOAD, slot)); slot++ }
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
            System.err.println("fixStackmapFrames error en $jar: $e")
        }
    }

    private fun fixWrongInitOwner(cn: ClassNode, needInit: MutableSet<Pair<String, String>>) {
        cn.methods.forEach { m ->
            val insns = m.instructions
            val pending = java.util.ArrayDeque<String>()
            var pendingDup: String? = null
            var i = 0
            while (i < insns.size()) {
                val n = insns[i]
                when {
                    n is TypeInsnNode && n.opcode == Opcodes.NEW -> pendingDup = n.desc
                    n is InsnNode && n.opcode == Opcodes.DUP && pendingDup != null -> {
                        pending.push(pendingDup)
                        pendingDup = null
                    }
                    n is MethodInsnNode && n.opcode == Opcodes.INVOKESPECIAL && n.name == "<init>" -> {
                        if (!pending.isEmpty()) {
                            val t = pending.pop()
                            if (n.owner != t) {
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
