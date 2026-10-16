# Desktop design system

Compose Desktop uses `dp` for layout and `sp` for text. Those values are not multiplied again for monitor DPI. A logical 14 in the product spec is 14.sp here, not 14.pt.

## Tokens

| Token | Light | Dark |
|---|---|---|
| Background | #F6F8FC | #0F172A |
| Surface | #FFFFFF | #172033 |
| Elevated | #FFFFFF | #1E293B |
| Text | #172033 | #F1F5F9 |
| Secondary text | #526176 | #B6C2D2 |
| Border | #DCE3EE | #334155 |
| Accent | #2563EB | #60A5FA |
| On accent | #FFFFFF | #0F172A |
| Success | #15803D | #4ADE80 |
| Warning | #92400E | #FBBF24 |
| Danger | #B91C1C | #F87171 |

Spacing uses 4, 8, 12, and 16. Cards and the inspector use a 12 radius. Buttons and fields use an 8 radius. The command bar is 64 high. The status line is 28 high. Navigation rows are 40 high. Icon buttons are 36.

## Breakpoints

Client size comes from the Compose window size in dp.

| Mode | Rule |
|---|---|
| Wide | width ≥ 1280 and height ≥ 360, and not widget |
| Standard | 960 ≤ width < 1280 and height ≥ 360 |
| Compact | otherwise, outside the widget |
| Widget enter | width < 600 or height < 360 |
| Widget exit | width ≥ 680 and height ≥ 420 |
| Manual widget | stays until Expand |

The macOS title bar is outside the Compose client. The AWT minimum size is 360×280 so the client is not clipped by that title bar. A pure 320×200 client was not measured.

## Widget

The widget reads the same jobs. It does not start a second engine. There is no sparkline, because a 60-second speed history is not collected yet. An empty queue says "No active downloads" and does not invent a speed.
