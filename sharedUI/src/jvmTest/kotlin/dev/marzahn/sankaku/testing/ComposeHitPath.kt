package dev.marzahn.sankaku.testing

import androidx.compose.runtime.collection.MutableVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.RootForTest

internal class ComposeHitPath(root: RootForTest) {
  private val tracker = root.read<Any>("this$0").also(::requireRootNodeOwner)
    .read<Any>("pointerInputEventProcessor")
    .read<Any>("hitPathTracker")

  fun nodes(): List<Modifier.Node> = nodesBelow(tracker.read("root"))

  private fun nodesBelow(parent: Any): List<Modifier.Node> =
    parent.read<MutableVector<*>>("children").asMutableList().filterNotNull().flatMap { node ->
      listOf(node.read<Modifier.Node>("modifierNode")) + nodesBelow(node)
    }
}

private const val ROOT_NODE_OWNER = "androidx.compose.ui.node.RootNodeOwner"

private fun requireRootNodeOwner(owner: Any) = check(owner.javaClass.name == ROOT_NODE_OWNER) {
  "Compose changed the test root's owner: it is a ${owner.javaClass.name}, no longer a $ROOT_NODE_OWNER"
}

private inline fun <reified T : Any> Any.read(field: String): T {
  val declared = generateSequence<Class<*>>(javaClass) { it.superclass }
    .firstNotNullOfOrNull { type -> type.declaredFields.firstOrNull { it.name == field } }
    ?: error("Compose moved ${javaClass.name}.$field, which the touch trace reads to find the hit path")
  declared.isAccessible = true
  return declared.get(this) as? T ?: error("Compose changed ${javaClass.name}.$field: no longer a ${T::class.simpleName}")
}
