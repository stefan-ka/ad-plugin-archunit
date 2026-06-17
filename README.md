# ArchUnit Plugin for Architectural Decision Enforcement

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

An [ad-guidance-tool](https://github.com/adr/ad-guidance-tool) enforcement plugin that compiles `code` rules into Java architecture tests using the [ArchUnit](https://www.archunit.org/) framework and JUnit 5. The generated `.java` files are compiled as part of a Maven or Gradle test project and run with `mvn test` or `./gradlew test`.

## Installation

Install from a GitHub release:

```sh
ade plugin install archunit --repo github.com/stefan-ka/ad-plugin-archunit
```

Or build from source using GraalVM native-image:

```sh
./gradlew nativeCompile
ade plugin install archunit --path ./build/native/nativeCompile/ade-plugin-archunit
```

## Prerequisites

The target Java test project must have the following dependencies on the test classpath:

- `com.tngtech.archunit:archunit-junit5` (>= 1.x)
- `org.junit.jupiter:junit-jupiter` (>= 5.x)

## Usage

### Compile

```sh
ade compile -i path/to/adr.rule -p archunit
```

The plugin writes one `ArchitectureFrom_<id>Test.java` file per rule file into the output directory. Add the generated file to your test source tree and run `mvn test` or `./gradlew test` in the target project to execute the generated tests.

### Verify

```sh
ade verify -i path/to/adr.rule -p archunit
```

In verify mode the plugin generates the same Java file, runs Maven or Gradle scoped to the generated test class, maps each test result back to its ADE rule name, and removes the generated file afterward.

### Configuration

Plugin-specific options are stored under the `plugin_configs.archunit` namespace and forwarded to the plugin at runtime. Set them with `ade config set` from the project root:

```sh
ade config set plugin_configs.archunit.output-dir               ./src/test/java/generated
ade config set plugin_configs.archunit.generated-tests-package  generated
ade config set plugin_configs.archunit.import-packages          com.example.app,com.example.infra
ade config set plugin_configs.archunit.test-project             ./
ade config set plugin_configs.archunit.build-tool               mvn
```

Pass `--global` to write the value to the user-level config instead of the project-level `.ade.yaml`.

| Config key                                         | Required for    | Description                                                                                          |
| -------------------------------------------------- | --------------- | ---------------------------------------------------------------------------------------------------- |
| `plugin_configs.archunit.output-dir`               | compile, verify | Directory in which to write the generated `.java` file. Defaults to `.`.                             |
| `plugin_configs.archunit.generated-tests-package`  | compile, verify | Java package declaration for the generated test class. Defaults to `generated`.                      |
| `plugin_configs.archunit.import-packages`          | compile, verify | Comma-separated list of base packages for `ClassFileImporter` to scan. Required for rules to match.  |
| `plugin_configs.archunit.test-project`             | verify          | Path to the root of the Maven or Gradle project that contains the generated test.                    |
| `plugin_configs.archunit.build-tool`               | verify          | `mvn` (default) or `gradle`.                                                                         |

## Supported rules

Only `code` blocks are processed. `file` and `custom` blocks are skipped with a warning.

| ADL assertion                     | ArchUnit condition                                                       |
| --------------------------------- | ------------------------------------------------------------------------ |
| `must not depend on`              | `noClasses()…should().dependOnClassesThat().resideInAnyPackage(…)`       |
| `must only depend on`             | `noClasses()…should().dependOnClassesThat().resideInAnyPackage(…)`       |
| `must be annotated with`          | `.should().beAnnotatedWith(…)`                                           |
| `must not be annotated with`      | `.should().notBeAnnotatedWith(…)`                                        |
| `must implement`                  | `.should().implement(…)`                                                 |
| `must not implement`              | `.should().notImplement(…)`                                              |
| `must extend`                     | `.should().beAssignableTo(…)`                                            |
| `must not extend`                 | `.should().notBeAssignableTo(…)`                                         |
| `must be in`                      | `.should().resideInAPackage(…)`                                          |
| `must not be in`                  | `.should().resideOutsideOfPackage(…)`                                    |
| `must match`                      | `.should().haveNameMatching(…)`                                          |
| `must not match`                  | `.should().notHaveNameMatching(…)`                                       |
| `must be public/internal/private` | `.should().bePublic()` / `.bePackagePrivate()` / `.bePrivate()`          |
| `must be abstract/sealed/static`  | `.should().beAbstract()` / `.beFinal()` / `.haveModifier(STATIC)`        |
| `must be acyclic`                 | `slices().matching(…).should().beFreeOfCycles()`                         |

`exclude` clauses are translated to `.and().<predicate>` entries in the `.that()` chain.

`must only depend on` is implemented by inverting the allowed set: every package selector defined in the rule file that is not explicitly listed as allowed is treated as forbidden and passed to `dependOnClassesThat().resideInAnyPackage(…)`.

Rules with `severity warning` wrap the `rule.check(…)` call in a try/catch block and print to stderr (non-fatal). Rules with `severity error` call `rule.check(…)` directly, which fails the test on violation.

## Unsupported rules

- `must only be accessed by`: ArchUnit checks outgoing dependencies; the plugin does not implement incoming dependency checks.

Skipped rules are noted with a comment block in the generated file.

## Known limitations

`ClassFileImporter.importPackages(…)` only scans the packages listed in `import-packages`. If a subject or target package is absent, rules targeting those classes pass vacuously. Ensure all relevant base packages are listed.

## License

Licensed under the [Apache License, Version 2.0](./LICENSE).
