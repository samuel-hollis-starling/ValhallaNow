# Fix for Array Folding Issue

## Problem
When typing `?` after `String` in `String @Nullable []`, the plugin adds `@Nullable` before `String`, creating `@Nullable String @Nullable []`. However, the folding was showing `String[]?` instead of the expected `String?[]?`.

## Root Cause
The issue had multiple contributing factors:
1. The `hasTypeUseNullableAnnotation` method wasn't reliably detecting the inner annotation in all PSI structures
2. Stale fold regions from before the outer annotation was added might persist
3. The fold region update was using `buildInitialFoldings()` which cannot be called from EDT

## Changes Made

### 1. Improved `hasTypeUseNullableAnnotation` (NullableFoldingBuilder.java)
- Added `PsiTreeUtil.getChildrenOfType` as a backup method to find type-use annotations
- This ensures we catch annotations even if the PSI structure is different than expected
- Added parent check to ensure we're only looking at direct children of the array type element

### 2. Added Text-Based Fallback in `handleArrayComponentNullability` (NullableFoldingBuilder.java)
- Added a text-based check to detect `@Nullable` between the type name and brackets
- This provides a fallback if the PSI-based detection fails
- Checks if the text before the `[` contains `@Nullable` or `Nullable`

### 3. Fixed Threading Issue in `triggerFoldingUpdate` (NullableTypedHandler.java)
- **Critical Fix**: Removed `buildInitialFoldings()` calls that were causing EDT threading errors
- Changed back to using `updateFoldRegions()` which is safe to call from EDT
- Kept the double update pattern (immediate + delayed) to ensure fold regions are refreshed
- The daemon restart combined with multiple `updateFoldRegions()` calls ensures stale regions are removed

## Expected Behavior After Fix

| Input | Expected Fold |
|-------|---------------|
| `@Nullable String[]` | `String?[]` |
| `String @Nullable []` | `String[]?` |
| `@Nullable String @Nullable []` | `String?[]?` |

## Testing
To test the fix:
1. Create a file with `private String @Nullable [] names;`
2. The fold should show `String[]?`
3. Type `?` after `String` (so it becomes `String?`)
4. The plugin should add `@Nullable` before `String`
5. The fold should now show `String?[]?` (not `String[]?`)

## Implementation Details

The fix addresses three potential failure points:
1. **PSI Detection**: Multiple methods to detect type-use annotations
2. **Text Fallback**: If PSI fails, parse the actual text
3. **Fold Region Refresh**: Multiple `updateFoldRegions()` calls with daemon restart to remove stale regions

### Threading Model
- All fold region operations happen on EDT (correct)
- PSI operations happen within write intent read actions (correct)
- Daemon restart triggers background reanalysis, then EDT updates the UI

This multi-layered approach ensures the folding works correctly even if the PSI structure varies or there are timing issues with fold region updates.
