## Propkonf property configuration language

PropKonf is a simple configuration language designed to allow creating structural key-value
config files. Its style is based on a mix of TOML, UNIX "dot-files", JSON, and simple property files.

#### Features

- Hierarchic scoped properties
- Block, line and scope comments
- Reusable variables
- Simple expression evaluation
- String, integer, and boolean types

#### Short introduction

Values (int, bool, string literals) are stored as key-value pairs, which can be nested is blocks:

```
ttl = 5

outer {
    inner {
        my-value = "hello"
    }

    scoped = true
}
```

Line comments (`//`), block comments (`/* ... */`), and scoped comments (`# expr { v = 10 }`) are allowed.

Variables must begin with `$` and are not available as values, but can be used to initialize values:

```
my-scope {
    // Varliable are scoped!
    $base-path = "/really/long/path/that/i/dont/wanna/repeat"
    log-loc = "${base-path}/log" // string interpolation
    bin-loc = "${base-path}/bin"
}
```

Simple expressions include math operations (`+`, `-`, `/`, `*`, `%`, ...), logic operations (`&&`, `!=`, ...)
null-checking/mapping (`?:`, `. :`). These operations can be used inside `${...}` blocks, can reference literals of variables.

String literals support:

- Normal strings (`"X: ${x}"` -> `X: 10`)
- Strings without interpolation (`x"X: ${x}"` -> `X: ${x}`)
- Multiline raw strings (`"""X: <<line break>> ${x}"""` -> `X: <<line break>> 10`)
- Multiline raw trimmed strings (`|"""A: <<line break>>    |1"` -> `A:<<line break>>1`)

#### Project structure

The project is compromised of three modules:

- Core: The internal logic and API of Propkonf
- Tools: A simple wrapper to allow command line operations
- Demo: A simple demo program showing some of the language's capabilities

#### Building and running

To build the project, run `mvn clean install` in the root directory of the project.

You can execute the output ot the tool module (a command line program) with
`java -jar tools/target/tools-$version-jar-with-dependencies.jar --help`, it will display the
allowed commands. This utility can be used to transform, examine, evaluate config files.

The demo module is recommended to be run from your normal dev workspace (IDE)
to allow you to fiddle values as you like, by default it uses its demo configuration file.

#### Sample configuration

```
script {
    // Block declaration, vars declared are scoped
    $base-path = "~/long/path/to/a/base/directory" // Variable declaration
    init = "${base-path}/start.sh" // String interpolation
    stop = "${base-path}/stop.sh"
    check = "${base-path}/check.sh"
}

# server {
    // blocks starting with # are ignored
    // they do have to be syntactically correct to parse tho

    web-port = 100
    console-port = 200
    log-port = 300
}

server {
    $port = ${PORT ?: 8000} // Environment variable passed, or else
    web-port = ${port + 1} // Simple expressions
    console-port = ${port + 2}
    log-port = ${port + 3}

    // Should probably be done programmatically, but showing off capabilities
    $needs-root = ${port < 1000}
    // ". :" is the conditional operator (~"? :" in Java)
    needs-root-message = "Root account: ${needs-root . "required on UNIX (?)" : "not required"}"

    cache { // Nested blocks
        enabled = true
        location = "/tmp/myapp/${external-loc ?: "unknown"}/cache"
        strategy = "lru"
    }
}

demo {
    inner {
        useless {
            // This is truly just for the demo
            x = ${1 + 2 + (100 - 94) / 2} // = 1 + 2 + (6 / 2) = 6
            b = ${(100 == (99 + 1)) && (2 % 3 == 2)} // = true && true = true
        }
    }
}

```
