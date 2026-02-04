N# Valhalla Now - Quick Reference Guide

## 🚀 Quick Start

### 1. Install Dependencies
Add to your project's `build.gradle.kts`:
```kotlin
dependencies {
    implementation("org.jspecify:jspecify:1.0.0")
}
```

### 2. Run the Plugin
```bash
./gradlew runIde
```

## 💡 Usage Examples

### Basic Types
```java
// Type this:
String? name;

// Plugin converts to:
@Nullable String name;

// Displays as:
String? name;
```

### Method Parameters
```java
// Type this:
public void setUser(String? name, Integer? age) { }

// Becomes:
public void setUser(@Nullable String name, @Nullable Integer age) { }
```

### Return Types
```java
// Type this:
public String? getName() { }

// Becomes:
public @Nullable String getName() { }
```

### Generics
```java
// Type this:
List<String?> names;

// Becomes:
List<@Nullable String> names;

// Complex:
Map<String, List<Integer?>> data;
// Becomes:
Map<String, List<@Nullable Integer>> data;
```

### Arrays

#### Nullable Array (array reference can be null)
```java
// Type this:
String[]? names;

// Becomes:
@Nullable String[] names;
```

#### Array of Nullable (elements can be null)
```java
// Type this:
String?[] names;

// Becomes:
String @Nullable [] names;
```

### Primitives (Error Cases)
```java
// Type this:
int? count;

// Result:
❌ Error hint: "Primitive types cannot be nullable"
// The ? is removed, no annotation added
```

## ⌨️ Keyboard Shortcuts

### Toggle Code Folding
- **macOS**: `Cmd + Shift + .` or `Cmd + =`
- **Windows/Linux**: `Ctrl + Shift + .` or `Ctrl + =`

### Expand/Collapse All Folds
- **macOS**: `Cmd + Shift + +` / `Cmd + Shift + -`
- **Windows/Linux**: `Ctrl + Shift + +` / `Ctrl + Shift + -`

## 🔍 What Gets Transformed?

| Context | Input | Output | Display |
|---------|-------|--------|---------|
| Field | `String? name` | `@Nullable String name` | `String? name` |
| Parameter | `void m(String? s)` | `void m(@Nullable String s)` | `void m(String? s)` |
| Return | `String? get()` | `@Nullable String get()` | `String? get()` |
| Generic | `List<String?>` | `List<@Nullable String>` | `List<String?>` |
| Array (nullable) | `String[]?` | `@Nullable String[]` | `String[]?` |
| Array (elements) | `String?[]` | `String @Nullable []` | `String?[]` |
| Multi-dim | `String[][]?` | `@Nullable String[][]` | `String[][]?` |

## 🚫 What Doesn't Work?

### Primitive Types
```java
int?      // ❌ Error
long?     // ❌ Error
double?   // ❌ Error
float?    // ❌ Error
boolean?  // ❌ Error
char?     // ❌ Error
byte?     // ❌ Error
short?    // ❌ Error
```

**Why?** Primitive types cannot be null in Java. Use wrapper classes instead:
```java
Integer?  // ✅ Works
Long?     // ✅ Works
Double?   // ✅ Works
// etc.
```

## 🛠️ Development Commands

### Build Plugin
```bash
./gradlew buildPlugin
```
Output: `build/distributions/ValhallaNow2-1.0-SNAPSHOT.zip`

### Run in Dev IDE
```bash
./gradlew runIde
```

### Compile Only
```bash
./gradlew compileJava
```

### Verify Plugin
```bash
./gradlew verifyPluginProjectConfiguration
```

### Clean Build
```bash
./gradlew clean build
```

## 📁 Project Structure

```
ValhallaNow2/
├── src/main/java/me/samhollis/valhalla/
│   ├── folding/
│   │   └── NullableFoldingBuilder.java    # Visual transformation
│   ├── typing/
│   │   └── NullableTypedHandler.java      # ? to @Nullable conversion
│   └── util/
│       └── TypeValidator.java              # Primitive validation
├── src/main/resources/META-INF/
│   └── plugin.xml                          # Plugin descriptor
├── src/test/java/                          # Test suite
├── src/test/testData/                      # Test fixtures
├── build.gradle.kts                        # Build configuration
├── README.md                               # Full documentation
├── IMPLEMENTATION_PLAN.md                  # Implementation details
├── IMPLEMENTATION_SUMMARY.md               # Completion summary
└── QUICK_REFERENCE.md                      # This file
```

## 🐛 Troubleshooting

### Import Not Added Automatically?
The plugin adds imports automatically. If not working:
1. Check you're in a Java file (not Kotlin)
2. Verify you're typing after a valid type identifier
3. Ensure the file is writable

### Folding Not Showing?
1. Check code folding is enabled: **Editor** → **General** → **Code Folding**
2. Try toggling folding: `Cmd/Ctrl + Shift + .`
3. Verify `@Nullable` annotation is from `org.jspecify.annotations`

### Primitive Error Not Showing?
The error hint appears briefly. Look for:
- Red error hint near the cursor
- The `?` character should be removed
- No annotation should be added

### Plugin Not Loading?
1. Check IDE version (must be 2024.2+)
2. Verify Java 21 is configured
3. Check logs: **Help** → **Show Log in Finder/Explorer**

## 📚 Additional Resources

- **JSpecify Documentation**: https://jspecify.dev/
- **Project Valhalla**: https://openjdk.org/projects/valhalla/
- **IntelliJ Plugin SDK**: https://plugins.jetbrains.com/docs/intellij/

## 💬 Tips & Tricks

1. **Batch Conversion**: Select multiple lines, type will work on cursor position
2. **Import Organization**: IDE auto-organizes imports, including @Nullable
3. **Code Style**: Format code after using ? to ensure proper spacing
4. **Quick Documentation**: Hover over folded ? to see full annotation
5. **Find Usages**: Works on both ? display and @Nullable annotation

## 🎯 Best Practices

1. **Consistency**: Use `?` syntax throughout your codebase
2. **Documentation**: Document nullable semantics in javadoc
3. **Validation**: Let the plugin prevent primitive nullability errors
4. **Reviews**: Folding makes code reviews easier to read
5. **Migration**: Convert existing `@Nullable` annotations gradually

## 🎉 Pro Tips

- **Multi-line**: `?` works on any type declaration, anywhere
- **Refactoring**: When renaming types, folding updates automatically
- **Copy/Paste**: Folding preserves underlying annotation structure
- **Search**: Can search for `@Nullable` even when viewing as `?`
- **Version Control**: Diffs show actual `@Nullable` annotations

---

**Happy Coding with Valhalla Syntax! 🚀**
