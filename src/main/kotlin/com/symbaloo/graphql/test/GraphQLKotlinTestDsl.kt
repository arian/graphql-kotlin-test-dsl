package com.symbaloo.graphql.test

import com.google.gson.GsonBuilder
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import org.opentest4j.AssertionFailedError

/**
 * Initial function to kick of the test DSL with the GraphQL schema object
 */
fun graphQLTest(schema: GraphQL, tester: GraphQLQueryBuilderDsl.() -> Unit): GraphQLResultActionsDsl {
    return GraphQLQueryBuilderDsl(schema).apply(tester).execute()
}

class GraphQLQueryBuilderDsl(
    private val schema: GraphQL,
    internal var query: String = ""
) {

    internal val variables = mutableMapOf<String, Any?>()
    internal var context: Any? = null
    internal var builder: ((ExecutionInput.Builder) -> Unit)? = null

    internal fun execute(): GraphQLResultActionsDsl {
        val executionResult = schema.execute {
            it.query(query)
            variables.takeIf { v -> v.isNotEmpty() }?.also { v -> it.variables(v) }
            context?.also { c -> it.context(c) }
            builder?.also { b -> b(it) }
            it
        }
        return GraphQLResultActionsDsl(executionResult)
    }
}

/**
 * Set the query to test
 */
fun GraphQLQueryBuilderDsl.query(query: String) {
    this.query = query
}

/**
 * Read .graphql query files from (test) resources
 */
fun GraphQLQueryBuilderDsl.queryFromFile(filename: String) {
    query(
            Thread.currentThread().contextClassLoader
                    ?.getResourceAsStream(filename)
                    .let { requireNotNull(it) }
                    .use { String(it.readBytes()) })
}

/**
 * Add a variable
 */
fun GraphQLQueryBuilderDsl.variable(name: String, value: Any?) {
    this.variables[name] = value
}

/**
 * Add multiple variables
 */
fun GraphQLQueryBuilderDsl.variables(map: Map<String, Any?>) {
    this.variables += map
}

/**
 * Set a context object
 */
fun GraphQLQueryBuilderDsl.context(context: Any) {
    this.context = context
}

/**
 * Use the graphql-java builder to set query inputs
 * @see ExecutionInput.Builder
 */
fun GraphQLQueryBuilderDsl.builder(builder: (ExecutionInput.Builder) -> Unit) {
    this.builder = builder
}

class GraphQLResultActionsDsl(internal val executionResult: ExecutionResult)

/**
 * @return [GraphQLResultActionsDsl] for asserting and checking results
 */
fun GraphQLResultActionsDsl.andExpect(expectations: GraphQLResultMatcherDsl.() -> Unit): GraphQLResultActionsDsl {
    GraphQLResultMatcherDsl(executionResult).expectations()
    return this
}

/**
 * @return [GraphQLResultActionsDsl] for asserting and checking results
 */
fun GraphQLResultActionsDsl.andExpectJson(expectations: GraphQLJsonResultMatcherDsl.() -> Unit): GraphQLResultActionsDsl {
    andExpect { json { expectations() } }
    return this
}

/**
 * Do something with the executing result
 * @see ExecutionResult
 */
fun GraphQLResultActionsDsl.andDo(action: (ExecutionResult) -> Unit): GraphQLResultActionsDsl {
    action(executionResult)
    return this
}

/**
 * Return the execution result
 * @see ExecutionResult
 */
fun GraphQLResultActionsDsl.andReturn(): ExecutionResult {
    return executionResult
}

/**
 * Return the data at a given path
 * @see ExecutionResult
 * @see GraphQLResultMatcherDsl.path
 */
fun <T> GraphQLResultActionsDsl.andReturnPath(path: String): T {
    return JsonPathContext(executionResult.getData<Any>()).readJsonPathOrFail(path)
}

class GraphQLResultMatcherDsl(internal val executionResult: ExecutionResult)

/**
 * Assert that there are no errors in the result
 */
fun GraphQLResultMatcherDsl.noErrors() {
    val errors = this.executionResult.errors
    if (errors.isNotEmpty()) {
        throw AssertionFailedError(
                """Expected no errors in the result.
                |
                |It got these errors:
                |
                |${errors.joinToString(">\n>\n") { it.message.prependIndent(">> ") }}
                |""".trimMargin(),
                emptyList<GraphQLError>(),
                errors
        )
    }
}

/**
 * Assert that some field has some value
 */
fun <T> GraphQLResultMatcherDsl.rootFieldEqualTo(key: String, expected: T) {
    when (val data = executionResult.getData<Any>()) {
        is Map<*, *> -> {
            val actual = data[key]
            if (actual != expected) {
                throw AssertionFailedError("Expected field with key: $key", actual, expected)
            }
        }
        else -> {
            throw AssertionFailedError("Expected root data to be a map and contain field(s)")
        }
    }
}

/**
 * Parse the [ExecutionResult] data into a [GraphQLJsonResultMatcherDsl] DSL
 */
fun <R> GraphQLResultMatcherDsl.json(fn: GraphQLJsonResultMatcherDsl.() -> R): R {
    val context = JsonPathContext(executionResult.getData<Any>())
    return GraphQLJsonResultMatcherDsl(context).fn()
}

/**
 * Get a [GraphQLJsonPathResultMatcherDsl] DSL for a certain [JsonPath] path
 */
fun <T, R> GraphQLResultMatcherDsl.path(jsonPath: String, fn: GraphQLJsonPathResultMatcherDsl<T>.() -> R): R {
    return json { path(jsonPath, fn) }
}

fun <T> GraphQLResultMatcherDsl.path(jsonPath: String, fn: GraphQLJsonPathResultMatcherDsl<T>.() -> Unit): Unit =
        path<T, Unit>(jsonPath, fn)

/**
 * Test a value in the response
 */
fun <T> GraphQLResultMatcherDsl.pathIsEqualTo(path: String, value: T) {
    return json { path<T, Unit>(path) { isEqualTo(value) } }
}

/**
 * Be able to do something (e.g. assertions) with the value at the given path
 */
fun <T, R> GraphQLResultMatcherDsl.pathAndDo(path: String, matcher: (T) -> R): R {
    return json { path<T, R>(path) { andDo(matcher) } }
}

class GraphQLJsonResultMatcherDsl internal constructor(internal val context: JsonPathContext)

/**
 * Use the [GraphQLJsonPathResultMatcherDsl] for a certain [JsonPath] path
 */
fun <T, R> GraphQLJsonResultMatcherDsl.path(path: String, fn: GraphQLJsonPathResultMatcherDsl<T>.() -> R): R {
    val value: T = context.readJsonPathOrFail(path)
    return GraphQLJsonPathResultMatcherDsl(path, context.data, value).fn()
}

fun <T> GraphQLJsonResultMatcherDsl.path(path: String, fn: GraphQLJsonPathResultMatcherDsl<T>.() -> Unit): Unit =
        path<T, Unit>(path, fn)

/**
 * Test the result of a [JsonPath] to be equal to a certain value
 */
fun <T> GraphQLJsonResultMatcherDsl.pathIsEqualTo(path: String, expected: T): Unit =
        path<T, Unit>(path) { isEqualTo(expected) }

/**
 * Be able to do something (e.g. assertions) with the value at the given path
 */
fun <T, R> GraphQLJsonResultMatcherDsl.pathAndDo(path: String, matcher: (T) -> R): R {
    return path<T, R>(path) { andDo(matcher) }
}

/**
 * Returns the [ExecutionResult] as a JSON string
 */
fun <R> GraphQLJsonResultMatcherDsl.doWithJsonString(fn: (String) -> R): R {
    return fn(context.json)
}

class GraphQLJsonPathResultMatcherDsl<out T>(internal val path: String, internal val data: Any, internal val value: T)

/**
 * Do something with the result
 */
fun <T, R> GraphQLJsonPathResultMatcherDsl<T>.andDo(matcher: (T) -> R): R {
    return matcher(value)
}

fun <T> GraphQLJsonPathResultMatcherDsl<T>.isEqualTo(expected: T) {
    if (expected != value) {
        throw AssertionFailedError("No match for path: $path\n\nIn data: $data", value, null)
    }
}

internal class JsonPathContext(
    internal val data: Any
) {
    internal val json = GsonBuilder().serializeNulls().create().toJson(data)
    private val context = JsonPath.parse(json)

    fun <T> readJsonPathOrFail(path: String): T {
        return try {
            context.read(path)
        } catch (e: PathNotFoundException) {
            throw AssertionError("No results for path: $path\n\nIn data: $data")
        }
    }
}
