# Focusly — Visual Design Specification (complete, for 1:1 Dayboard rebuild)

Sources read in full: `src/index.css`, `tailwind.config.ts`, `components.json`, `index.html`, `src/components/ui/button.tsx`, `src/components/ui/card.tsx`, `src/components/ui/dialog.tsx`.

---

## 1. Theming architecture

- shadcn/ui "default" style, `baseColor: "slate"`, CSS-variables mode (`cssVariables: true`), no Tailwind class prefix (`prefix: ""`), TSX components, non-RSC. Aliases: `@/components`, `@/lib/utils`, `@/components/ui`, `@/lib`, `@/hooks`.
- All color tokens are CSS custom properties holding **raw HSL triplets** (`H S% L%`, no `hsl()` wrapper). Tailwind maps each to `hsl(var(--token))`. Consumers can therefore apply alpha via slash syntax (`bg-primary/90` → `hsl(var(--primary) / 0.9)`).
- **Dark mode**: Tailwind `darkMode: ["class"]` — dark is activated by adding the class `dark` to the root (`<html>`) element.
- **Accent theme**: selected via a `data-theme` attribute on the root element. Six themes: `coral` (default), `ocean`, `forest`, `lavender`, `ember`, `slate`. Coral doubles as the fallback: its light values are declared on `:root, [data-theme="coral"]` and its dark values on `[data-theme="coral"].dark, :root.dark`. Every other theme declares `[data-theme="X"]` (light) and `[data-theme="X"].dark` (dark) blocks.
- So the full theme matrix is 6 accent themes x 2 modes = 12 palettes. All are defined in `@layer base` in `src/index.css`.
- Shared radius token, declared once on `:root`: `--radius: 0.75rem` (12px).
- **Sidebar tokens exist only in the coral blocks** (light and dark). The other five themes do not redefine them, so sidebar colors always fall back to the coral `:root` values (coral light set in light mode; in dark mode the `:root.dark` selector supplies the coral dark sidebar set). Reproduce this exactly: sidebar palette does not change per accent theme.

## 2. Complete color token tables (all values are HSL triplets: `H S% L%`)

### 2.1 Coral (default) — light (`:root, [data-theme="coral"]`)
| Token | Value |
|---|---|
| --background | 350 30% 97% |
| --foreground | 350 25% 15% |
| --card | 350 20% 99% |
| --card-foreground | 350 25% 15% |
| --popover | 350 20% 99% |
| --popover-foreground | 350 25% 15% |
| --primary | 350 91% 60% |
| --primary-foreground | 0 0% 100% |
| --secondary | 160 59% 45% |
| --secondary-foreground | 0 0% 100% |
| --muted | 350 16% 94% |
| --muted-foreground | 350 12% 42% |
| --accent | 350 16% 90% |
| --accent-foreground | 350 25% 15% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 350 14% 89% |
| --input | 350 14% 89% |
| --ring | 350 91% 60% |
| --sidebar-background | 350 20% 97% |
| --sidebar-foreground | 350 15% 26% |
| --sidebar-primary | 350 25% 15% |
| --sidebar-primary-foreground | 0 0% 98% |
| --sidebar-accent | 350 14% 94% |
| --sidebar-accent-foreground | 350 25% 15% |
| --sidebar-border | 350 14% 89% |
| --sidebar-ring | 350 91% 60% |

### 2.2 Coral — dark (`[data-theme="coral"].dark, :root.dark`)
| Token | Value |
|---|---|
| --background | 222 20% 10% |
| --foreground | 210 20% 92% |
| --card | 222 18% 14% |
| --card-foreground | 210 20% 92% |
| --popover | 222 18% 14% |
| --popover-foreground | 210 20% 92% |
| --primary | 350 91% 60% |
| --primary-foreground | 0 0% 100% |
| --secondary | 160 59% 45% |
| --secondary-foreground | 0 0% 100% |
| --muted | 222 14% 18% |
| --muted-foreground | 215 15% 55% |
| --accent | 222 14% 22% |
| --accent-foreground | 210 20% 92% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 222 14% 20% |
| --input | 222 14% 20% |
| --ring | 350 91% 60% |
| --sidebar-background | 222 18% 12% |
| --sidebar-foreground | 210 20% 85% |
| --sidebar-primary | 210 20% 92% |
| --sidebar-primary-foreground | 222 18% 12% |
| --sidebar-accent | 222 14% 18% |
| --sidebar-accent-foreground | 210 20% 92% |
| --sidebar-border | 222 14% 20% |
| --sidebar-ring | 350 91% 60% |

### 2.3 Ocean — light (`[data-theme="ocean"]`)
| Token | Value |
|---|---|
| --background | 200 35% 96% |
| --foreground | 210 30% 14% |
| --card | 200 30% 98% |
| --card-foreground | 210 30% 14% |
| --popover | 200 30% 98% |
| --popover-foreground | 210 30% 14% |
| --primary | 199 89% 48% |
| --primary-foreground | 0 0% 100% |
| --secondary | 172 66% 50% |
| --secondary-foreground | 0 0% 100% |
| --muted | 200 22% 92% |
| --muted-foreground | 210 16% 44% |
| --accent | 200 22% 88% |
| --accent-foreground | 210 30% 14% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 200 20% 87% |
| --input | 200 20% 87% |
| --ring | 199 89% 48% |

### 2.4 Ocean — dark (`[data-theme="ocean"].dark`)
| Token | Value |
|---|---|
| --background | 215 28% 9% |
| --foreground | 210 20% 92% |
| --card | 215 25% 13% |
| --card-foreground | 210 20% 92% |
| --popover | 215 25% 13% |
| --popover-foreground | 210 20% 92% |
| --primary | 199 89% 48% |
| --primary-foreground | 0 0% 100% |
| --secondary | 172 66% 50% |
| --secondary-foreground | 0 0% 100% |
| --muted | 215 20% 18% |
| --muted-foreground | 215 15% 55% |
| --accent | 215 18% 22% |
| --accent-foreground | 210 20% 92% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 215 18% 20% |
| --input | 215 18% 20% |
| --ring | 199 89% 48% |

### 2.5 Forest — light (`[data-theme="forest"]`)
| Token | Value |
|---|---|
| --background | 140 30% 96% |
| --foreground | 150 30% 12% |
| --card | 140 25% 98% |
| --card-foreground | 150 30% 12% |
| --popover | 140 25% 98% |
| --popover-foreground | 150 30% 12% |
| --primary | 142 71% 45% |
| --primary-foreground | 0 0% 100% |
| --secondary | 47 96% 53% |
| --secondary-foreground | 150 25% 14% |
| --muted | 140 20% 92% |
| --muted-foreground | 150 14% 40% |
| --accent | 140 20% 87% |
| --accent-foreground | 150 30% 12% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 140 18% 86% |
| --input | 140 18% 86% |
| --ring | 142 71% 45% |

### 2.6 Forest — dark (`[data-theme="forest"].dark`)
| Token | Value |
|---|---|
| --background | 150 20% 8% |
| --foreground | 138 16% 90% |
| --card | 150 18% 12% |
| --card-foreground | 138 16% 90% |
| --popover | 150 18% 12% |
| --popover-foreground | 138 16% 90% |
| --primary | 142 71% 45% |
| --primary-foreground | 0 0% 100% |
| --secondary | 47 96% 53% |
| --secondary-foreground | 150 25% 14% |
| --muted | 150 14% 17% |
| --muted-foreground | 140 10% 52% |
| --accent | 150 14% 21% |
| --accent-foreground | 138 16% 90% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 150 14% 19% |
| --input | 150 14% 19% |
| --ring | 142 71% 45% |

### 2.7 Lavender — light (`[data-theme="lavender"]`)
| Token | Value |
|---|---|
| --background | 268 32% 96% |
| --foreground | 270 28% 16% |
| --card | 268 28% 98% |
| --card-foreground | 270 28% 16% |
| --popover | 268 28% 98% |
| --popover-foreground | 270 28% 16% |
| --primary | 263 70% 71% |
| --primary-foreground | 0 0% 100% |
| --secondary | 330 80% 65% |
| --secondary-foreground | 0 0% 100% |
| --muted | 268 20% 92% |
| --muted-foreground | 270 14% 43% |
| --accent | 268 20% 88% |
| --accent-foreground | 270 28% 16% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 268 18% 87% |
| --input | 268 18% 87% |
| --ring | 263 70% 71% |

### 2.8 Lavender — dark (`[data-theme="lavender"].dark`)
| Token | Value |
|---|---|
| --background | 270 22% 9% |
| --foreground | 270 15% 90% |
| --card | 270 20% 13% |
| --card-foreground | 270 15% 90% |
| --popover | 270 20% 13% |
| --popover-foreground | 270 15% 90% |
| --primary | 263 70% 71% |
| --primary-foreground | 0 0% 100% |
| --secondary | 330 80% 65% |
| --secondary-foreground | 0 0% 100% |
| --muted | 270 16% 18% |
| --muted-foreground | 270 12% 53% |
| --accent | 270 16% 22% |
| --accent-foreground | 270 15% 90% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 270 16% 20% |
| --input | 270 16% 20% |
| --ring | 263 70% 71% |

### 2.9 Ember — light (`[data-theme="ember"]`)
| Token | Value |
|---|---|
| --background | 28 35% 96% |
| --foreground | 20 28% 13% |
| --card | 28 30% 98% |
| --card-foreground | 20 28% 13% |
| --popover | 28 30% 98% |
| --popover-foreground | 20 28% 13% |
| --primary | 25 95% 53% |
| --primary-foreground | 0 0% 100% |
| --secondary | 43 96% 56% |
| --secondary-foreground | 20 25% 15% |
| --muted | 28 22% 91% |
| --muted-foreground | 20 14% 41% |
| --accent | 28 22% 87% |
| --accent-foreground | 20 28% 13% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 28 20% 86% |
| --input | 28 20% 86% |
| --ring | 25 95% 53% |

### 2.10 Ember — dark (`[data-theme="ember"].dark`)
| Token | Value |
|---|---|
| --background | 20 22% 9% |
| --foreground | 30 18% 90% |
| --card | 20 20% 13% |
| --card-foreground | 30 18% 90% |
| --popover | 20 20% 13% |
| --popover-foreground | 30 18% 90% |
| --primary | 25 95% 53% |
| --primary-foreground | 0 0% 100% |
| --secondary | 43 96% 56% |
| --secondary-foreground | 20 25% 15% |
| --muted | 20 16% 17% |
| --muted-foreground | 20 10% 52% |
| --accent | 20 16% 21% |
| --accent-foreground | 30 18% 90% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 20 16% 19% |
| --input | 20 16% 19% |
| --ring | 25 95% 53% |

### 2.11 Slate — light (`[data-theme="slate"]`)
| Token | Value |
|---|---|
| --background | 220 20% 95% |
| --foreground | 224 22% 16% |
| --card | 220 16% 98% |
| --card-foreground | 224 22% 16% |
| --popover | 220 16% 98% |
| --popover-foreground | 224 22% 16% |
| --primary | 215 16% 47% |
| --primary-foreground | 0 0% 100% |
| --secondary | 215 25% 60% |
| --secondary-foreground | 0 0% 100% |
| --muted | 220 18% 90% |
| --muted-foreground | 220 12% 44% |
| --accent | 220 18% 86% |
| --accent-foreground | 224 22% 16% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 220 16% 85% |
| --input | 220 16% 85% |
| --ring | 215 16% 47% |

### 2.12 Slate — dark (`[data-theme="slate"].dark`)
| Token | Value |
|---|---|
| --background | 224 20% 9% |
| --foreground | 220 14% 90% |
| --card | 224 18% 13% |
| --card-foreground | 220 14% 90% |
| --popover | 224 18% 13% |
| --popover-foreground | 220 14% 90% |
| --primary | 215 20% 55% |
| --primary-foreground | 0 0% 100% |
| --secondary | 215 25% 60% |
| --secondary-foreground | 0 0% 100% |
| --muted | 224 14% 17% |
| --muted-foreground | 220 10% 52% |
| --accent | 224 14% 21% |
| --accent-foreground | 220 14% 90% |
| --destructive | 0 84% 60% |
| --destructive-foreground | 0 0% 100% |
| --border | 224 14% 19% |
| --input | 224 14% 19% |
| --ring | 215 20% 55% |

Notes:
- Slate is the ONLY theme whose `--primary` differs between light (215 16% 47%) and dark (215 20% 55%). All other themes keep the same primary/secondary in both modes.
- `--destructive: 0 84% 60%` with white foreground is identical across all 12 palettes.
- Forest and Ember use a dark `--secondary-foreground` (150 25% 14% and 20 25% 15% respectively) because their secondary (yellow/amber) is light; all other themes use white secondary-foreground.
- `--input` always equals `--border`; `--ring` always equals `--primary` in every palette.

### Convenience hex approximations of each theme's primary (for icons/marketing; the HSL values above are canonical)
- coral primary 350 91% 60% ≈ #F63E5C; secondary 160 59% 45% ≈ #2FB789
- ocean primary 199 89% 48% ≈ #0EA5E9; secondary 172 66% 50% ≈ #2BD4BD
- forest primary 142 71% 45% ≈ #21C45D; secondary 47 96% 53% ≈ #FACC15
- lavender primary 263 70% 71% ≈ #A78BEA; secondary 330 80% 65% ≈ #ED5EA8
- ember primary 25 95% 53% ≈ #F97416; secondary 43 96% 56% ≈ #FBBD23
- slate light primary 215 16% 47% ≈ #65758B, dark primary 215 20% 55% ≈ #75879E

## 3. Typography

- Fonts loaded via CSS `@import` at the top of `src/index.css` from Google Fonts:
  `https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@500;700&display=swap`
  - Inter: weights 400, 500, 600, 700.
  - JetBrains Mono: weights 500, 700.
  - `display=swap`.
- Tailwind `fontFamily` (replaces the default families, not extend):
  - `sans`: `['Inter', 'sans-serif']`
  - `mono`: `['JetBrains Mono', 'monospace']`
- `body` rule: `bg-background text-foreground font-sans antialiased` plus explicit `font-family: 'Inter', sans-serif;`. So the entire app is antialiased Inter on the `--background` color.
- Custom utility `.font-mono-timer` → `font-family: 'JetBrains Mono', monospace;` (used for the timer digits).
- Component type scale (from the shadcn primitives):
  - CardTitle: `text-2xl font-semibold leading-none tracking-tight` (24px, weight 600, line-height 1, letter-spacing -0.025em).
  - CardDescription / DialogDescription: `text-sm text-muted-foreground` (14px).
  - DialogTitle: `text-lg font-semibold leading-none tracking-tight` (18px, 600).
  - Buttons: `text-sm font-medium` (14px, 500).

## 4. Radii, shadows, borders, shape language

- Base radius token: `--radius: 0.75rem` (12px).
- Tailwind borderRadius mapping (extend):
  - `rounded-lg` = `var(--radius)` = 12px
  - `rounded-md` = `calc(var(--radius) - 2px)` = 10px
  - `rounded-sm` = `calc(var(--radius) - 4px)` = 8px
- Global border color: `* { @apply border-border; }` — every element's default border color is `hsl(var(--border))`.
- Shape language: soft-rounded (12px cards/dialogs, 10px buttons), 1px hairline borders in the theme's border tone, subtle shadows. No gradients and no glassmorphism/backdrop-blur are defined anywhere in these files (the dialog overlay is flat `bg-black/80`, no blur).
- Shadows used: Tailwind defaults only — `shadow-sm` on Card (`0 1px 2px 0 rgb(0 0 0 / 0.05)`), `shadow-lg` on DialogContent (`0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)`).

## 5. Layout / container / breakpoints

- Tailwind `container`: `center: true`, `padding: "2rem"`, screens override only `"2xl": "1400px"` (so the container max-width caps at 1400px on 2xl; all other breakpoints are Tailwind defaults: sm 640, md 768, lg 1024, xl 1280).
- Tailwind `content` globs: `./pages/**/*.{ts,tsx}`, `./components/**/*.{ts,tsx}`, `./app/**/*.{ts,tsx}`, `./src/**/*.{ts,tsx}`.
- Plugin: `tailwindcss-animate` (supplies `animate-in`/`animate-out`, `fade-in-0`, `zoom-in-95`, `slide-in-from-*` used by the dialog).
- `index.html` viewport meta: `width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no` (pinch zoom disabled).
- App title: `Focusly` (rename to Dayboard). Meta description: "A distraction-free Pomodoro timer with a built-in to-do list to help you stay focused and productive." `lang="en"`. Icons: `/favicon.png` (PNG) and `/apple-touch-icon.png`. og:type `website`, twitter card `summary_large_image` (og image is a Lovable-hosted signed URL — replace for the clone).
- HTML body contains only `<div id="root"></div>` plus module script `/src/main.tsx`.

## 6. Custom animations & keyframes

From `src/index.css` (`@layer utilities`):
- `.task-done` → `animation: task-complete 0.3s ease-out forwards;`
  - `@keyframes task-complete { 0% { opacity: 1; } 100% { opacity: 0.4; } }` (completed task fades to 40% opacity and stays there).
- `.timer-pulse` → `animation: timer-pulse 2s ease-in-out infinite;`
  - `@keyframes timer-pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.85; } }` (gentle 2s breathing pulse on the running timer).

From `tailwind.config.ts` (extend):
- keyframes `accordion-down`: from `height: 0` to `height: var(--radix-accordion-content-height)`; `accordion-up` is the reverse.
- animations: `accordion-down 0.2s ease-out`, `accordion-up 0.2s ease-out`.

## 7. Themed scrollbars (global, outside layers)

- All elements: `scrollbar-width: thin; scrollbar-color: hsl(var(--muted-foreground) / 0.3) transparent;` (Firefox).
- WebKit: `*::-webkit-scrollbar { width: 6px; height: 6px; }`; track transparent; thumb `background-color: hsl(var(--muted-foreground) / 0.25)` with `border-radius: 9999px`; thumb hover `hsl(var(--muted-foreground) / 0.4)`.

## 8. Component shape specs (shadcn primitives)

### 8.1 Button (`src/components/ui/button.tsx`, cva-based, Radix Slot `asChild` support)
Base classes (all variants): `inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ring-offset-background transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0`
- i.e. 10px radius, 14px/500 text, 8px gap, color transition, focus-visible: 2px ring in `--ring` with 2px offset on `--background`; disabled: 50% opacity + no pointer events; child SVG icons forced to 16x16px, no shrink, no pointer events.

Variants (exact):
- `default`: `bg-primary text-primary-foreground hover:bg-primary/90`
- `destructive`: `bg-destructive text-destructive-foreground hover:bg-destructive/90`
- `outline`: `border border-input bg-background hover:bg-accent hover:text-accent-foreground`
- `secondary`: `bg-secondary text-secondary-foreground hover:bg-secondary/80`
- `ghost`: `hover:bg-accent hover:text-accent-foreground` (transparent at rest)
- `link`: `text-primary underline-offset-4 hover:underline`

Sizes (exact):
- `default`: `h-10 px-4 py-2` (40px tall, 16px horiz padding)
- `sm`: `h-9 rounded-md px-3` (36px, 12px)
- `lg`: `h-11 rounded-md px-8` (44px, 32px)
- `icon`: `h-10 w-10` (40x40 square)

Default variant/size: `default`/`default`.

### 8.2 Card (`src/components/ui/card.tsx`)
- Card: `rounded-lg border bg-card text-card-foreground shadow-sm` (12px radius, 1px `--border` border, `--card` bg, small shadow).
- CardHeader: `flex flex-col space-y-1.5 p-6` (24px padding, 6px vertical gap).
- CardTitle: `<h3>` with `text-2xl font-semibold leading-none tracking-tight`.
- CardDescription: `<p>` `text-sm text-muted-foreground`.
- CardContent: `p-6 pt-0` (24px padding, no top).
- CardFooter: `flex items-center p-6 pt-0`.

### 8.3 Dialog (`src/components/ui/dialog.tsx`, Radix Dialog)
- Overlay: `fixed inset-0 z-50 bg-black/80` + `data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0` (80% black scrim, fade in/out; no blur).
- Content: `fixed left-[50%] top-[50%] z-50 grid w-full max-w-lg translate-x-[-50%] translate-y-[-50%] gap-4 border bg-background p-6 shadow-lg duration-200` plus enter/exit: `fade-in-0/out-0`, `zoom-in-95/out-95`, `slide-in-from-left-1/2`, `slide-in-from-top-[48%]` (and matching slide-out), `sm:rounded-lg` (square corners on mobile, 12px radius from the sm breakpoint up). Centered, max-width 512px (`max-w-lg`), 24px padding, 16px grid gap, 1px border, `--background` bg, large shadow, 200ms animations.
- Close button: absolute `right-4 top-4`, `rounded-sm opacity-70`, `ring-offset-background transition-opacity`, `data-[state=open]:bg-accent data-[state=open]:text-muted-foreground`, `hover:opacity-100`, `focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2`, `disabled:pointer-events-none`. Icon: lucide `X` at `h-4 w-4` (16px). Screen-reader-only label text: `Close`.
- DialogHeader: `flex flex-col space-y-1.5 text-center sm:text-left` (centered text on mobile, left-aligned from sm up).
- DialogFooter: `flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2` (stacked, reversed on mobile; right-aligned row with 8px spacing from sm up).
- DialogTitle: `text-lg font-semibold leading-none tracking-tight`.
- DialogDescription: `text-sm text-muted-foreground`.

## 9. Rebuild checklist for Dayboard (Compose for Web)

1. Implement the 12-palette token system exactly as in Section 2, keyed by (accentTheme in {coral, ocean, forest, lavender, ember, slate}) x (light|dark); coral is the default and fallback; sidebar tokens are coral-only and mode-dependent.
2. Load Inter (400/500/600/700) and JetBrains Mono (500/700) from Google Fonts with `display=swap`; body = Inter, antialiased, on `--background`/`--foreground`.
3. Radii: 12/10/8px (lg/md/sm) derived from a single 12px base token.
4. Reproduce button variants/sizes, card, and dialog exactly per Section 8, including hover alphas (primary/90, destructive/90, secondary/80), focus rings (2px ring + 2px offset), disabled 50% opacity, and dialog enter/exit fade+zoom-95 over 200ms behind an 80%-black scrim.
5. Reproduce `task-complete` (0.3s ease-out to 0.4 opacity, forwards) and `timer-pulse` (2s ease-in-out infinite, 1→0.85→1 opacity) animations, JetBrains Mono timer digits, thin 6px pill scrollbars tinted with muted-foreground at 25%/40% alpha.
6. Container: centered, 2rem padding, max 1400px at 2xl; standard Tailwind breakpoints otherwise. Viewport disables user zoom.
7. No gradients, no glassmorphism/backdrop-blur anywhere; flat surfaces, hairline `--border` borders, Tailwind default `shadow-sm`/`shadow-lg` shadows.
