import field.Field
import field.FieldImpl
import stack.Path
import stack.Stack

class AggregateContext(
    private val localId: ID,
    private val messages: Map<ID, Map<Path, *>>,
    private val previousState: Map<Path, *>) {

    private val stack: Stack<Any> = Stack()
    private val state: MutableMap<Path, Any?> = mutableMapOf()
    private val toBeSent: MutableMap<Path, Any?> = mutableMapOf()

    fun messagesToSend(): Map<Path, *> = toBeSent.toMap()
    fun newState(): Map<Path, *> = state.toMap()

    fun <X> neighbouring(type: X): Field<X> {
        toBeSent[stack.currentPath()] = type
        val messages = messagesAt(stack.currentPath())
        return FieldImpl(localId, mapOf(localId to type) + messages)
    }

    @Suppress("UNCHECKED_CAST")
    fun <X,Y : Any> repeating(initial: X, repeat: (X) -> Y): Y {
        val res = if (previousState.containsKey(stack.currentPath())) {
            repeat(previousState[stack.currentPath()] as X)
        } else {
            repeat(initial)
        }
        state[stack.currentPath()] = res
        return res
    }

    fun <X, Y: Any?> sharing(initial: X, body: (Field<X>) -> Y): Y {
        val messages = messagesAt(stack.currentPath())
        val previous = if (previousState.containsKey(stack.currentPath())) {
            (previousState[stack.currentPath()])
        } else {
            initial
        }
        val subject = FieldImpl<X>(localId, mapOf(localId to previous) + messages)
        return body(subject).also {
            toBeSent[stack.currentPath()] = it
            state[stack.currentPath()] = it
        }
    }

    /**
     * Alignment function that pushes in the stack the pivot, executes the body and pop the last
     * element of the stack after it is called.
     * Returns the body's return element.
     */
    fun <R> alignedOn(pivot: Any?, body: () -> R): R {
        stack.alignRaw(pivot)
        return body().also { stack.dealign() }
    }

    private fun messagesAt(path: Path): Map<ID, *> = messages.mapNotNull { (id, message) ->
        if (message.containsKey(path)) id to message[path] else null
    }.toMap()

    data class AggregateResult<X>(val result: X, val toSend: Map<Path, *>, val newState: Map<Path, *>)
}

/**
 * Aggregate program entry point which computes a single iteration, taking as parameters the previous state.
 * @param localId: id of the device.
 * @param messages: map with all the messages sent by the neighbors.
 * @param state: last device state.
 * @param init: lambda with AggregateContext object receiver that provides the aggregate constructs.
 */
fun <X> aggregate(
    localId: ID = IntId(),
    messages: Map<ID, Map<Path, *>> = emptyMap<ID, Map<Path, Any>>(),
    state: Map<Path, *> = emptyMap<Path, Any>(),
    init: AggregateContext.() -> X
) = singleCycle(localId, messages, state, compute = init)

/**
 * Aggregate program entry point which computes multiple iterations.
 * @param condition: lambda that establish the number of iterations.
 * @param network: data structure used to allow the local communication between devices.
 * @param init: lambda with AggregateContext object receiver that provides the aggregate constructs.
 */
fun <X> aggregate(
    condition: () -> Boolean,
    network: Network = NetworkImpl(),
    init: AggregateContext.() -> X)
= runUntil(condition, network, compute = init)
