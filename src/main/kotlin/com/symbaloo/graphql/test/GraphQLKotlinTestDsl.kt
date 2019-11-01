package com.symbaloo.graphql.test

import com.google.gson.Gson
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

    internal val variables = mutableMapOf<String, Any>()
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
 * Add a variable
 */
fun GraphQLQueryBuilderDsl.variable(name: String, value: Any) {
    this.variables[name] = value
}

/**
 * Add multiple variables
 */
fun GraphQLQueryBuilderDsl.variables(map: Map<String, Any>) {
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
 * @return ResultActions for asserting and checking results
 */
fun GraphQLResultActionsDsl.andExpect(expectations: GraphQLResultMatcherDsl.() -> Unit): GraphQLResultActionsDsl {
    GraphQLResultMatcherDsl(this.executionResult).expectations()
    return this
}

/**
 * Do something with the executing result
 * @see ExecutionResult
 */
fun GraphQLResultActionsDsl.andDo(action: (ExecutionResult) -> Unit): GraphQLResultActionsDsl {
    action(this.executionResult)
    return this
}

/**
 * Return the execution result
 * @see ExecutionResult
 */
fun GraphQLResultActionsDsl.andReturn(): ExecutionResult {
    return this.executionResult
}

/**
 * Return the data at a given path
 * @see ExecutionResult
 * @see GraphQLResultMatcherDsl.path
 */
fun <T> GraphQLResultActionsDsl.andReturnPath(path: String): T {
    return readJsonPath(path, executionResult)
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
 * Get a JsonResultMatcher DSL
 */
fun <T> GraphQLResultMatcherDsl.path(path: String, fn: GraphQLJsonResultMatcherDsl<T>.() -> Unit) {
    val data = executionResult.getData<Any>()
    val value = readJsonPath<T>(path, executionResult)
    GraphQLJsonResultMatcherDsl<T>(path, data, value).fn()
}

/**
 * Test a value in the response
 */
fun <T> GraphQLResultMatcherDsl.pathIsEqualTo(path: String, value: T) {
    path<T>(path) { isEqualTo(value) }
}

/**
 * Be able to do something (e.g. assertions) with the value at the given path
 */
fun <T> GraphQLResultMatcherDsl.withPath(path: String, matcher: (T) -> Unit) {
    path<T>(path) { andDo(matcher) }
}

class GraphQLJsonResultMatcherDsl<T>(internal val path: String, internal val data: Any, internal val value: T)

/**
 * Return value at path
 */
fun <T> GraphQLJsonResultMatcherDsl<T>.read(): T {
    return value
}

/**
 * Do something with the result
 */
fun <T> GraphQLJsonResultMatcherDsl<T>.andDo(matcher: (T) -> Unit) {
    matcher(read())
}

fun <T> GraphQLJsonResultMatcherDsl<T>.isEqualTo(expected: T) {
    val value: T = read()
    if (expected != value) {
        throw AssertionFailedError("No match for path: $path\n\nIn data: $data", value, null)
    }
}

private fun <T> readJsonPath(path: String, executionResult: ExecutionResult): T {
    val data = executionResult.getData<Any>()
    val json = Gson().toJson(data)
    return try {
        JsonPath.read<T>(json, path)
    } catch (e: PathNotFoundException) {
        throw AssertionError("No results for path: $path\n\nIn data: $data")
    }
}
