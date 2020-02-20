Kotlin GraphQL Test DSL for graphql-java
========================================

This is a kotlin DSL to easily write (integration) tests for your [graphql-java](https://github.com/graphql-java/graphql-java)
application.

It is inspired by the [Spring MockMVC DSL](https://docs.spring.io/spring/docs/current/spring-framework-reference/languages.html#mockmvc-dsl)
and lets you use [JsonPath](https://github.com/json-path/JsonPath) to quickly retrieve results from the response.


[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.symbaloo/graphql-kotlin-test-dsl/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.symbaloo/graphql-kotlin-test-dsl)
![Actions](https://github.com/arian/graphql-kotlin-test-dsl/workflows/CI/badge.svg)

### Gradle

```kotlin
testImplementation("com.symbaloo:graphql-kotlin-test-dsl:1.0.5")
```

### Maven

```xml
<dependency>
  <groupId>com.symbaloo</groupId>
  <artifactId>graphql-kotlin-test-dsl</artifactId>
  <version>1.0.5</version>
</dependency>
```

Example
-------

The following code shows an example how to use this test library.

```kotlin
// the graphql-java schema
val schema: GraphQL = createTestSchema()
val result: ExecutionResult = graphQLTest(schema) {
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
}
    // check for noErrors
    .andExpect { noErrors() }
    // create json context
    .andExpectJson {
        // go into the result with a json path
        path<String>("$.hello.hello") {
            // quick isEqualTo check
            isEqualTo("world")
            // do something with the result
            andDo {
                assertThat(it).isEqualTo("world")
            }
        }
        // combination of `path` and `andDo`
        pathAndDo("$.hello") { it: Map<String, Any> ->
            assertThat(it).contains("hello", "world")
        }

        // combination of `path` and `isEqualTo`
        pathIsEqualTo("$.echo", "response")

        // it can also return values
        val hello = pathAndDo("$.hello") { map: Map<String, Any> ->
            map["hello"]
        }
        assertThat(hello).isEqualTo("world")
    }
    .andReturn()
```

### Link with your Assertion Library

```kotlin
graphQLTest(createTestSchema()) {
    query("{ answer }")
}.andExpectJson {
    assertPath("$.answer").isEqualTo(42)
}

fun GraphQLJsonResultMatcherDsl.assertPath(path: String): Assert<Any?> =
    pathAndDo(path) { it: Any? -> assertThat(it) }
```

License
-------

MIT License
