# Typing ? Folding Synchronization Fix

## Problem
When typing `?` after a type (e.g., `String?`), the typed handler would insert `@Nullable` before the type, but the folding would show incorrect results using stale PSI data. For example:
- Typing `?` after `String` in `String @Nullable []` would temporarily show `String[]?` instead of `String?[]?`
- The correct folding would only appear after revisiting the file later

## Root Cause
The folding builder (`NullableFoldingBuilder`) was using cached/stale PSI data that didn't reflect the document changes made by the typed handler. When `?` was converted to `@Nullable`, the folding descriptors were calculated before PSI was fully synchronized with the document.

## Solution

### 1. Enhanced PSI Synchronization in `NullableTypedHandler`
**File:** `NullableTypedHandler.java`

**Changes:**
- Added `saveAllDocuments()` call to force document persistence before folding updates
- Restructured `triggerFoldingUpdate()` with multiple stages:
  1. Commit all documents and save
  2. Remove all existing nullable fold regions
  3. Wait 50ms, then commit again and trigger daemon restart
  4. Wait 100ms, then update fold regions
  5. Wait 150ms, then do final fold region update and collapse

**Why this works:**
- Multiple commit cycles ensure PSI is fully reparsed from document
- Delays give the IntelliJ platform time to invalidate caches
- Removing old fold regions forces complete rebuild
- Multiple `updateFoldRegions()` calls ensure descriptors are recalculated

### 2. Added Validation in `NullableFoldingBuilder`
**File:** `NullableFoldingBuilder.java`

**Changes:**
- Added `isInBounds()` to validate PSI element ranges are within document bounds
- Added `isValidFoldingRange()` to validate fold ranges before creating descriptors
- Added PSI text vs document text comparison in `processAnnotation()` - if they don't match, skip processing (stale PSI)
- Updated `handleArrayComponentNullability()` to use **document text** instead of PSI text for the fallback type-use annotation detection
- Added `Document` parameter to all folding handler methods for validation

**Why this works:**
- Detects when PSI is out of sync with document and skips creating invalid fold regions
- Using document text directly ensures we're working with the actual current state
- Range validation prevents crashes from stale offset data

### 3. Fixed `@Nullable String @Nullable[]` → `String?[]?` Handling
**File:** `NullableFoldingBuilder.java`

**Changes:**
- Added `hasModifierListNullableAnnotation()` method to detect if there's a `@Nullable` on the modifier list
- Updated `handleArrayTypeUseAnnotation()` to check for modifier list annotation and add `?` after component type when both annotations are present

**Why this works:**
- When processing the type-use `@Nullable` (after `String`), it now checks if there's also a modifier list `@Nullable` (before `String`)
- If both exist, the placeholder becomes `String?[]?` instead of `String[]?`
- The `hasOuterNullableAnnotation()` check ensures we don't create duplicate folds

## Key Insights

1. **Document text is the source of truth**: When PSI might be stale, always validate against document text
2. **Multiple synchronization cycles**: Single commit isn't enough - need multiple passes with delays
3. **Validate before creating descriptors**: Check ranges are valid and text matches before creating fold regions
4. **Fallback to text-based checks**: When PSI structure is unreliable, parse the document text directly

## Expected Behavior After Fix

### Basic typing:
- `String` → type `?` → immediately shows `String?` (folded `@Nullable String`)

### Array typing:
- `String[]` → type `?` at end → immediately shows `String[]?` (folded `String @Nullable []`)
- `String` → type `?` then `[]` → immediately shows `String?[]` (folded `@Nullable String[]`)

### Double nullable arrays:
- `@Nullable String @Nullable[]` → immediately shows `String?[]?`
- Typing `?` after `String` in `String @Nullable[]` → immediately shows `String?[]?`

### Generics:
- `List<String>` → type `?` inside `<>` after `String` → immediately shows `List<String?>`
- `@Nullable List<@Nullable String>` → immediately shows `List<String?>?`

## Testing

To verify the fix:
1. Build and install the plugin
2. Open a Java file
3. Type variable declarations with `?` syntax:
   - `String?` - should immediately fold
   - `String?[]` - should immediately fold  
   - `String[]?` - should immediately fold
   - Type `?` after `String` in existing `String @Nullable[]` - should immediately update to `String?[]?`
4. Check that folding is correct without needing to reopen the file

## Date
February 5, 2026
