# Valhalla Now - IntelliJ IDEA Plugin

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![IntelliJ Platform](https://img.shields.io/badge/IntelliJ-2024.2%2B-blue)
![Java](https://img.shields.io/badge/Java-21-orange)

Bring Project Valhalla's nullable type syntax to IntelliJ IDEA **today**! This plugin provides visual transformation and typing assistance for nullable types using JSpecify annotations.

## 🚀 Features

### Visual Transformation (Code Folding)
Automatically displays `@Nullable` annotations in the familiar `?` syntax from Project Valhalla:

- **Fields**: `@Nullable String` → `String?`
- **Parameters**: `void setName(@Nullable String name)` → `void setName(String? name)`
- **Return Types**: `@Nullable Integer getValue()` → `Integer? getValue()`
- **Generics**: `List<@Nullable String>` → `List<String?>`
- **Arrays**: 
  - `@Nullable String[]` → `String[]?` (nullable array)
  - `String @Nullable []` → `String?[]` (array of nullable elements)
- **Nested Generics**: `Map<String, List<@Nullable Integer>>` → `Map<String, List<Integer?>>`

### Typing Assistance
Type `?` after any type to automatically insert the `@Nullable` annotation:

- Automatically adds `@Nullable` annotation
- Automatically adds import for `org.jspecify.annotations.Nullable`
- Works in all type contexts (fields, parameters, return types, generics, arrays)
- **Smart Validation**: Prevents nullable primitives with helpful error messages

### Example

**Before:**
```java
import org.jspecify.annotations.Nullable;

public class User {
    private @Nullable String name;
    private @Nullable Integer age;
    
    public void setData(@Nullable String name, @Nullable Integer age) {
        this.name = name;
        this.age = age;
    }
    
    public @Nullable String getName() {
        return name;
    }
}
```

**After (with folding enabled):**
```java
import org.jspecify.annotations.Nullable;

public class User {
    private String? name;
    private Integer? age;
    
    public void setData(String? name, Integer? age) {
        this.name = name;
        this.age = age;
    }
    
    public String? getName() {
        return name;
    }
}
```

**Typing: Just type `String?` and the plugin converts it to `@Nullable String`!**

## 📦 Installation

### From JetBrains Marketplace (Coming Soon)
1. Open IntelliJ IDEA
2. Go to **Settings/Preferences** → **Plugins**
3. Search for "Valhalla Now"
4. Click **Install**
5. Restart IntelliJ IDEA

### From Source
```bash
git clone https://github.com/yourusername/ValhallaNow2.git
cd ValhallaNow2
./gradlew buildPlugin
```

The plugin will be built in `build/distributions/`.

## 🔧 Requirements

- **IntelliJ IDEA**: 2024.2 or later
- **Java**: 21+
- **Dependency**: [JSpecify](https://jspecify.dev/) annotations (`org.jspecify:jspecify:1.0.0`)

Add JSpecify to your project:

**Maven:**
```xml
<dependency>
    <groupId>org.jspecify</groupId>
    <artifactId>jspecify</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Gradle:**
```kotlin
implementation("org.jspecify:jspecify:1.0.0")
```

## 🎯 Usage

### Enabling Visual Transformation
Code folding is **enabled by default**. All `@Nullable` annotations will automatically display as `?` syntax.

To toggle code folding:
- **Mac**: `Cmd + Shift + .` or `Cmd + =`
- **Windows/Linux**: `Ctrl + Shift + .` or `Ctrl + =`

### Using Typing Assistance

1. Type your variable/parameter/field declaration as normal
2. After the type name, type `?`
3. The plugin will:
   - Remove the `?` character
   - Insert `@Nullable` before the type
   - Add the import statement if needed

**Example:**
```java
// Type: private String? name;
// Result: private @Nullable String name;
```

### Arrays

The plugin supports both array nullability patterns:

**Nullable Array** (the array reference itself can be null):
```java
// Type: String[]? names;
// Result: @Nullable String[] names;
// Display: String[]?
```

**Array of Nullable Elements** (elements can be null, array cannot):
```java
// Type: String?[] names;  
// Result: String @Nullable [] names;
// Display: String?[]
```

### Primitive Types

The plugin prevents nullable primitives and shows an error:

```java
// Type: int? count;
// Result: Error hint - "Primitive types cannot be nullable"
```

## 🏗️ Architecture

### Components

1. **`NullableFoldingBuilder`** (`me.samhollis.valhalla.folding`)
   - Extends `FoldingBuilderEx`
   - Identifies `@Nullable` annotations and creates folding regions
   - Transforms annotations to `?` syntax visually
   - Handles all contexts: fields, parameters, return types, generics, arrays

2. **`NullableTypedHandler`** (`me.samhollis.valhalla.typing`)
   - Extends `TypedHandlerDelegate`
   - Intercepts `?` character input
   - Inserts `@Nullable` annotations
   - Manages imports automatically
   - Validates against primitive types

3. **`TypeValidator`** (`me.samhollis.valhalla.util`)
   - Utility class for type validation
   - Checks for primitive types
   - Prevents invalid nullable annotations

### Plugin Descriptor
- **ID**: `me.samhollis.valhallanow`
- **Compatibility**: IntelliJ Platform 2024.2+
- **Dependencies**: `com.intellij.modules.platform`, `com.intellij.modules.java`

## 🛠️ Development

### Building

```bash
./gradlew build
```

### Running in Development IDE

```bash
./gradlew runIde
```

This will start a new IntelliJ IDEA instance with the plugin installed for testing.

### Testing

```bash
./gradlew test
```

Test coverage includes:
- Type validation (all primitive types)
- Folding for all contexts
- Typing assistance scenarios
- Error handling

### Project Structure

```
ValhallaNow2/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── me/samhollis/valhalla/
│   │   │       ├── folding/
│   │   │       │   └── NullableFoldingBuilder.java
│   │   │       ├── typing/
│   │   │       │   └── NullableTypedHandler.java
│   │   │       └── util/
│   │   │           └── TypeValidator.java
│   │   └── resources/
│   │       └── META-INF/
│   │           └── plugin.xml
│   └── test/
│       ├── java/
│       │   └── me/samhollis/valhalla/
│       │       ├── folding/
│       │       │   └── NullableFoldingBuilderTest.java
│       │       ├── typing/
│       │       │   └── NullableTypedHandlerTest.java
│       │       └── util/
│       │           └── TypeValidatorTest.java
│       └── testData/
│           ├── folding/
│           └── typing/
├── build.gradle.kts
├── settings.gradle.kts
├── IMPLEMENTATION_PLAN.md
└── README.md
```

## 📝 Design Decisions

### Array Notation
Java allows annotations in two positions for arrays:
- `@Nullable String[]` - The array reference can be null
- `String @Nullable []` - Array elements can be null

The plugin supports both patterns with corresponding `?` syntax:
- `String[]?` for nullable arrays
- `String?[]` for arrays with nullable elements

### Multi-dimensional Arrays
Following the Valhalla pattern:
- `String[][]?` - nullable 2D array
- `String?[][]` - 2D array of nullable elements

### Wildcard Generics
The plugin supports wildcards with nullable types:
- `List<? extends @Nullable String>` → `List<? extends String?>`

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🙏 Acknowledgments

- [Project Valhalla](https://openjdk.org/projects/valhalla/) - For the nullable type syntax inspiration
- [JSpecify](https://jspecify.dev/) - For standardized nullness annotations
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/) - For the excellent plugin development framework

## 📧 Contact

Samuel Hollis - support@samhollis.me

Project Link: [https://github.com/samhollis/ValhallaNow2](https://github.com/samhollis/ValhallaNow2)

---

**Note**: This plugin uses JSpecify's `@Nullable` annotation. While Project Valhalla is still in development, this plugin brings the syntax to your IDE today, improving code readability and preparing your codebase for the future!
