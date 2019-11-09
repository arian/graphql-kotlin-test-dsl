package com.symbaloo.graphql.test

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.key
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.idl.RuntimeWiring.newRuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private data class Bar(val foo: String)

private val schema = """
    |type Query {
    |    hello: Bar
    |    answer: Int
    |    echo(echo: String): String
    |    fromContext: String
    |    list: [Bar!]!
    |}
    |type Bar {
    |    hello: String
    |    infinite: Bar
    |}
    |""".trimMargin()

class GraphQLKotlinTestDslTest {

    private fun createTestSchema(): GraphQL {
        val schemaParser = SchemaParser()
        val typeDefinitionRegistry = schemaParser.parse(schema)
        val runtimeWiring = newRuntimeWiring()
            .type("Query") { builder ->
                builder.dataFetcher("hello") { Bar("world") }
                builder.dataFetcher("answer") { 42 }
                builder.dataFetcher("echo") { it.arguments["echo"] }
                builder.dataFetcher("fromContext") { it.getContext<Bar>().foo }
                builder.dataFetcher("list") { listOf(Bar("first"), Bar("second")) }
            }
            .type("Bar") { builder ->
                builder.dataFetcher("hello") { it.getSource<Bar>().foo }
                builder.dataFetcher("infinite") {
                    val bar = it.getSource<Bar>()
                    bar.copy(foo = bar.foo.repeat(2))
                }
            }
            .build()

        val schemaGenerator = SchemaGenerator()
        val graphQLSchema = schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring)
        return GraphQL.newGraphQL(graphQLSchema).build()
    }

    @Test
    fun `everything is fine`() {
        // the graphql-java schema
        val schema: GraphQL = createTestSchema()
        graphQLTest(schema) {
            // define a query
            query(
                """
                |query Init(${"$"}echo: String) {
                |    echo(echo: ${"$"}echo)
                |    hello { hello }
                |}""".trimMargin()
            )
            // add a variable
            variable("echo", "response")
        }.andExpect {
            // do assertions
            noErrors()
            pathIsEqualTo("echo", "response")
            doWithPath<String?>("\$.hello.hello") {
                assertThat(it).isEqualTo("world")
            }
            doWithPath<Map<String, Any>>("\$.hello") {
                assertThat(it).contains("hello", "world")
            }
        }
    }

    @Nested
    inner class GraphQLQueryBuilderDsl {

        @Test
        fun `variable dsl`() {
            graphQLTest(createTestSchema()) {
                query("query X(\$echo: String) { echo(echo: \$echo) }")
                variable("echo", "world")
            }.andExpect {
                noErrors()
                pathIsEqualTo("\$.echo", "world")
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
                pathIsEqualTo("\$.echo", "world")
                pathIsEqualTo("\$.e2", "second")
            }
        }

        @Test
        fun `context dsl`() {
            graphQLTest(createTestSchema()) {
                query("{ fromContext }")
                context(Bar("hey"))
            }.andExpect {
                noErrors()
                rootFieldEqualTo("fromContext", "hey")
            }
        }

        @Test
        fun `builder dsl`() {
            graphQLTest(createTestSchema()) {
                query("query X(\$echo: String) { fromContext echo(echo: \$echo) }")
                builder { input ->
                    input.context(Bar("ctx"))
                    input.variables(mapOf("echo" to "abc"))
                }
            }.andExpect {
                noErrors()
                rootFieldEqualTo("echo", "abc")
                rootFieldEqualTo("fromContext", "ctx")
            }
        }
    }

    @Nested
    @DisplayName("expect field to equal")
    inner class GraphQLResultActionsDsl {

        @Test
        fun andExpect() {
            graphQLTest(createTestSchema()) {
                query("{ answer }")
            }.andExpect {
                rootFieldEqualTo("answer", 42)
            }
        }

        @Test
        fun andDo() {
            graphQLTest(createTestSchema()) {
                query("{ answer }")
            }.andDo {
                assertThat(it.errors).hasSize(0)
            }
        }

        @Test
        fun `return execution result`() {
            val result = graphQLTest(createTestSchema()) {
                query("{ answer }")
            }.andReturn()

            assertThat(result).all {
                isInstanceOf(ExecutionResult::class.java)
                transform { it.getData<Map<String, Any>>()["answer"] }
                    .isEqualTo(42)
            }
        }

        @Test
        fun andReturnPath() {
            val result: String = graphQLTest(createTestSchema()) {
                query("{ hello { infinite { hello } } }")
            }.andReturnPath("$.hello.infinite.hello")
            assertThat(result).isEqualTo("worldworld")
        }

        @Test
        fun `andAsJson shortcut`() {
            graphQLTest(createTestSchema()) {
                query("{ hello { infinite { hello } } }")
            }.andAsJson {
                path<String>("$.hello.infinite.hello") {
                    isEqualTo("worldworld")
                }
                doWithPath<Map<String, Any>>("$.hello") {
                    assertThat(it).key("infinite").isNotNull()
                }
            }
        }
    }

    @Nested
    @DisplayName("expect field to equal")
    inner class GraphQLResultMatcherDsl {

        @Test
        fun `expect field`() {
            graphQLTest(createTestSchema()) {
                query("{ answer }")
            }.andExpect {
                noErrors()
                rootFieldEqualTo("answer", 42)
            }
        }

        @Test
        fun pathIsEqualTo() {
            graphQLTest(createTestSchema()) {
                query("{ hello { infinite { infinite { hello } } } }")
            }.andExpect {
                noErrors()
                pathIsEqualTo("$.hello.infinite.infinite.hello", "worldworldworldworld")
            }
        }

        @Test
        fun `pathIsEqualTo check with a list`() {
            graphQLTest(createTestSchema()) {
                query("{ list { hello } }")
            }.andExpect {
                noErrors()
                pathIsEqualTo("$.list.*.hello", listOf("first", "second"))
            }
        }

        @Test
        fun `json expectation`() {
            graphQLTest(createTestSchema()) {
                query("{ answer }")
            }.andExpect {
                noErrors()

                json {
                    path<Int>("answer") { isEqualTo(42) }
                    doWithJsonString {
                        assertThat(it).isEqualTo("""{"answer":42}""")
                    }
                }

                val result1 = json { doWithPath<Int, Int>("$.answer") { it * 2 } }
                assertThat(result1).isEqualTo(84)

                val result2 = json { path<Int, Int>("$.answer") { read() } }
                assertThat(result2).isEqualTo(42)
            }
        }

        @Test
        fun doWithPath() {
            graphQLTest(createTestSchema()) {
                query("{ hello { infinite { hello } } }")
            }.andExpect {
                noErrors()
                doWithPath<String>("$.hello.infinite.hello") {
                    assertThat(it).isEqualTo("worldworld")
                }
            }
        }
    }

    @Nested
    inner class GraphQLJsonPathResultMatcherDsl {

        @Test
        fun `json path read`() {
            graphQLTest(createTestSchema()) {
                query("{ hello { infinite { hello } } }")
            }.andExpect {
                path<String>("$.hello.infinite.hello") {
                    val x = read()
                    assertThat(x).isEqualTo("worldworld")
                }
            }
        }

        @Test
        fun `json path andDo`() {
            graphQLTest(createTestSchema()) {
                query("{ hello { infinite { hello } } }")
            }.andExpect {
                noErrors()
                path<String>("$.hello.infinite.hello") {
                    val world = andDo {
                        assertThat(it).isEqualTo("worldworld")
                        it
                    }
                    assertThat("hello $world").isEqualTo("hello worldworld")
                }
            }
        }

        @Test
        fun `json path dsl isEqualTo`() {
            graphQLTest(createTestSchema()) {
                query("{ hello { infinite { infinite { hello } } } }")
            }.andExpect {
                noErrors()
                assertThat(path<String>("$.hello.infinite.infinite.hello") {
                    isEqualTo("worldworldworldworld")
                })
            }
        }
    }
}
