package dev.sweety.config.yml

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import java.io.IOException
import java.io.Reader
import java.io.Writer
import java.lang.reflect.Type

object YamlUtils {

    private val yaml: ThreadLocal<Yaml> = ThreadLocal.withInitial {
        val yamlDumperOptions = DumperOptions()
        yamlDumperOptions.defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        yamlDumperOptions.indent = 2
        yamlDumperOptions.width = 80
        yamlDumperOptions.isPrettyFlow = false

        val yamlLoaderOptions = LoaderOptions()
        yamlLoaderOptions.maxAliasesForCollections = Int.MAX_VALUE
        yamlLoaderOptions.codePointLimit = Int.MAX_VALUE

        Yaml(Constructor(yamlLoaderOptions), FlowListRepresenter(yamlDumperOptions), yamlDumperOptions)
    }

    class FlowListRepresenter(options: DumperOptions) : Representer(options) {

        override fun representScalar(tag: Tag, value: String, style: DumperOptions.ScalarStyle?): Node {
            if (tag == Tag.STR) return super.representScalar(tag, value, DumperOptions.ScalarStyle.DOUBLE_QUOTED)
            return super.representScalar(tag, value, style)
        }

        override fun representSequence(tag: Tag, sequence: Iterable<*>, flowStyle: DumperOptions.FlowStyle?): Node {
            val node = super.representSequence(tag, sequence, flowStyle)
            if (node is SequenceNode) {
                // forza FLOW solo per le liste
                node.flowStyle = DumperOptions.FlowStyle.FLOW
            }
            return node
        }
    }

    @JvmStatic
    fun yaml(): Yaml = yaml.get()

    @JvmStatic
    fun <T> write(obj: T): String = yaml().dump(obj)

    @JvmStatic
    fun <T> write(obj: T, type: Type): String = yaml().dump(obj)

    @JvmStatic
    fun <T> save(config: T, appendable: Appendable) {
        if (appendable is Writer) {
            yaml().dump(config, appendable)
        } else {
            try {
                appendable.append(write(config))
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }

    @JvmStatic
    fun <T> save(config: T, type: Type, writer: Appendable) {
        save(config, writer)
    }

    @JvmStatic
    fun <T> load(reader: Reader, configClass: Class<T>): T = yaml().loadAs(reader, configClass)

    @JvmStatic
    fun <T> read(obj: String, clazz: Class<T>): T = yaml().loadAs(obj, clazz)

    @JvmStatic
    fun <T> load(reader: Reader, configClass: Type): T {
        if (configClass is Class<*>) {
            @Suppress("UNCHECKED_CAST")
            return yaml().loadAs(reader, configClass as Class<T>)
        }
        return yaml().load(reader)
    }

    @JvmStatic
    fun <T> read(obj: String, clazz: Type): T {
        if (clazz is Class<*>) {
            @Suppress("UNCHECKED_CAST")
            return yaml().loadAs(obj, clazz as Class<T>)
        }
        return yaml().load(obj)
    }
}
