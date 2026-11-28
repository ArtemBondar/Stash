# Stash design system

Stash opens to quick capture and the Inbox. A person can save a link or note offline with the editor and one primary action. The Inbox remains readable while a save is in progress.

## Design direction

Use a Stash palette with Material 3 controls, text styles, and interaction feedback in shared screens. This keeps the shared component set small. Follow native conventions for the operating system share sheet, window controls, menu bar, and keyboard shortcuts. References are [Google Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3) and [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/foundations).

## First screens

```text
Compact                                  Wide, from 720 dp of content width

Quick capture                            Quick capture | Inbox         | Item details
[ Link or text          ]                [ Link or text]| [ Search    ] | Selected item
[ Save to Inbox ]                        [ Save        ]| Selected row | Full original text
Save status                              Save status   | Other rows   | independently scrolls

Inbox
[ Search Inbox          ]
Newest item
Older items
```

- Compact uses one vertical feed. Wide keeps capture, search results, and the detail pane visible together. The list and detail receive more width than capture as the window grows. Future collection navigation gets its own design pass.
- Show a clear selected row in wide mode and preserve selection through resizing while the item remains in the current results. Use a short preview in wide rows and full original text in the detail pane. Keep the compact feed readable.
- Search and capture use persistent field labels. Capture keeps its draft on failure, disables duplicate submission during a save, and confirms success only after local persistence. Capture and search continue offline with the same controls and local data.
- List states cover loading, empty Inbox, no matches, and read failure with Retry. Detail shows guidance before selection. A stale result should never appear as the current detail while the Inbox is loading or unavailable.

## Shared foundations

| Color role | Light | Dark |
| --- | --- | --- |
| Surface | `#FAFAF7` | `#141916` |
| Pane surface | `#F0F3EE` | `#1E2722` |
| Text | `#1C2822` | `#E5EEE7` |
| Secondary text | `#48594F` | `#B3C5B9` |
| Primary action and focus | `#20593F` | `#A4D7BA` |
| Text on primary action | `#FFFFFF` | `#113826` |
| Selected row | `#D9EBDF` | `#2A4737` |
| Text on selected row | `#173B29` | `#DCF0E2` |
| Error text | `#9C2927` | `#FFB4AB` |

- Use system type via Material typography. Use title large for pane headings, body large for original content, body medium for previews, and body small for supporting copy. Allow text scaling and wrapping.
- Use 8 dp gaps between related elements, 16 dp pane padding, and 48 dp minimum targets in shared screens. Use an 8 dp corner radius for selected rows. Group the interface with spacing and surface color.
- Follow the system light or dark preference. Verify text contrast at 4.5:1 for normal text and ensure keyboard focus is visible. Give interactive rows a name, action, and selected state. Use text for save and error feedback.
- Keep tokens semantic and use standard controls where possible. Apply the same foundations to existing screens and components incrementally. Preserve platform-specific behavior and verify the affected capture flow after each change.

The standalone desktop capture window uses the same spacing, text roles, and minimum action height. Its shortcut guidance documents Enter to save, Shift+Enter for a newline, and Escape to close. A failed write keeps the draft and displays retry guidance without exposing storage exception details. Interactive resizing, keyboard, and light/dark appearance still need desktop verification.
