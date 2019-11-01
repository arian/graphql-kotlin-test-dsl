package com.symbaloo.graphql.test

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.junit.jupiter.api.Test

private data class Bar(val foo: String)

private val schema = """
    |type Query {
    |    hello: Bar
    |    echo(echo: String): String
    |}
    |type Bar {
    |    hello: String
    |}
    |""".trimMargin()

class GraphQLKotlinDslKtTest {

    private fun createTestSchema(): GraphQL {
        val schemaParser = SchemaParser()
        val typeDefinitionRegistry = schemaParser.parse(schema)
        val runtimeWiring = newRuntimeWiring()
            .type("Query") { builder ->
                builder.dataFetcher("hello") { Bar("world") }
                builder.dataFetcher("echo") { it.arguments["echo"] }
            }
            .type("Bar") { builder ->
                builder.dataFetcher("hello") { it.getSource<Bar>().foo }
            }
            .build()

        val schemaGenerator = SchemaGenerator()
        val graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
        return GraphQL.newGraphQL(graphQLSchema).build()
    }

    @Test
    fun `everything is fine`() {
        graphQLTest(createTestSchema()) {
            query("{ hello { hello } }")
        }.andExpect {
            noErrors()
            assertPath<String>("\$.hello.hello") {
                assertThat(it).isEqualTo("world")
            }
            assertPath<Map<String, Any>>("\$.hello") {
                assertThat(it).contains("hello", "world")
            }
        }
    }

    @Test
    fun `variable dsl`() {
        graphQLTest(createTestSchema()) {
            query("query X(\$echo: String) { echo(echo: \$echo) }")
            variable("echo", "world")
        }.andExpect {
            noErrors()
            path<String>("\$.echo") { it == "world" }
        }
    }

    @Test
    fun `variables dsl`() {
        val q = """
            |query X($${"echo"}: String, $${"e2"}: String) {
            |   echo: echo(echo: $${"echo"})
            |   e2: echo(echo: $${"e2"})
            |}""".trimMargin()
        graphQLTest(createTestSchema()) {
            query(q)
            variables(mapOf("echo" to "world", "e2" to "second"))
        }.andExpect {
            noErrors()
            path<String>("\$.echo") { it == "world" }
            path<String>("\$.e2") { it == "second" }
        }
    }
}
