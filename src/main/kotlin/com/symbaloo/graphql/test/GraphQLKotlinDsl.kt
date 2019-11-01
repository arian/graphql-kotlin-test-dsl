package com.symbaloo.graphql.test

import com.google.gson.Gson
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphQLError
import org.opentest4j.AssertionFailedError

fun graphQLTest(schema: GraphQL, tester: GraphQLKotlinDsl.() -> Unit): GraphQLResultActionsDsl {
    return GraphQLKotlinDsl(schema).apply(tester).execute()
}

class GraphQLKotlinDsl(
    private val schema: GraphQL,
    internal var query: String = ""
) {

    internal val variables = mutableMapOf<String, Any>()

    internal fun execute(): GraphQLResultActionsDsl {
        val executionResult = schema.execute {
            it.query(query)
            if (variables.isNotEmpty()) {
                it.variables(variables)
            }
            it
        }
        return GraphQLResultActionsDsl(executionResult)
    }
}

fun GraphQLKotlinDsl.query(query: String) {
    this.query = query
}

fun GraphQLKotlinDsl.variable(name: String, value: Any) {
    this.variables[name] = value
}

fun GraphQLKotlinDsl.variables(map: Map<String, Any>) {
    this.variables += map
}

class GraphQLResultActionsDsl(internal val executionResult: ExecutionResult)

fun GraphQLResultActionsDsl.andExpect(expectations: GraphQLResultMatcherDsl.() -> Unit): GraphQLResultActionsDsl {
    GraphQLResultMatcherDsl(this.executionResult).expectations()
    return this
}

class GraphQLResultMatcherDsl(internal val executionResult: ExecutionResult)

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

fun GraphQLResultMatcherDsl.path(path: String): GraphQLJsonResultMatcherDsl {
    val data = executionResult.getData<Any>()
    val json = Gson().toJson(data)
    return GraphQLJsonResultMatcherDsl(path, data, json)
}

fun <T> GraphQLResultMatcherDsl.path(path: String, matcher: (T) -> Boolean) {
    return path(path).value(matcher)
}

fun <T> GraphQLResultMatcherDsl.assertPath(path: String, matcher: (T) -> Unit) {
    return path(path).assertPath(matcher)
}

class GraphQLJsonResultMatcherDsl(internal val path: String, internal val data: Any, internal val json: String) {
    internal fun <T> getContent(): T {
        try {
            return JsonPath.read<T>(json, path)
        } catch (e: PathNotFoundException) {
            throw AssertionError("No results for path: $path\n\nIn data: $data")
        }
    }
}

fun <T> GraphQLJsonResultMatcherDsl.value(matcher: (T) -> Boolean) {
    this.assertPath { value: T ->
        if (!matcher(value)) {
            throw AssertionFailedError("No match for path: $path\n\nIn data: $data", value, null)
        }
    }
}

fun <T> GraphQLJsonResultMatcherDsl.assertPath(matcher: (T) -> Unit) {
    matcher(getContent())
}
