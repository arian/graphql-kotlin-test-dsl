Kotlin GraphQL Test DSL for graphql-java
========================================

This is a kotlin DSL to easily write (integration) tests for your [graphql-java](https://github.com/graphql-java/graphql-java)
application.

It is inspired by the [Spring MockMVC DSL](https://docs.spring.io/spring/docs/current/spring-framework-reference/languages.html#mockmvc-dsl)
and lets you use [JsonPath](https://github.com/json-path/JsonPath) to quickly retrieve results from the response.

![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.symbaloo/graphql-kotlin-test-dsl/badge.svg)
![Actions](https://github.com/arian/graphql-kotlin-test-dsl/workflows/CI/badge.svg)

### Gradle

```kotlin
testImplementation("com.symbaloo:grapql-kotlin-test-dsl:1.0.1")
```

### Maven

```xml
<dependency>
  <groupId>com.symbaloo</groupId>
  <artifactId>graphql-kotlin-test-dsl</artifactId>
  <version>1.0.1</version>
</dependency>
```

Example
-------

The following code shows an example how to use this test library.

```kotlin
// the graphql-java schema
val schema: GraphQL = createTestSchema()
graphQLTest(schema) {
    // define a query
    query("""
        |query Init(${"$"}echo: String) {
        |    echo(echo: ${"$"}echo)
        |    hello { hello }
        |}""".trimMargin())
    // add a variable
    variable("echo", "response")
}.andExpect {
    // do assertions
    noErrors()
    pathIsEqualTo("echo", "response")
    withPath<String?>("\$.hello.hello") {
        assertThat(it).isEqualTo("world")
    }
    withPath<Map<String, Any>>("\$.hello") {
        assertThat(it).contains("hello", "world")
    }
}
```

License
-------

MIT License
