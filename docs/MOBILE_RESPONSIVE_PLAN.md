# Mobile Responsive Plan — React Admin Dashboard

## Problem

The React dashboard is desktop-only. Every page breaks on mobile screens (<768px):

- **Sidebar** is fixed at 220px with no collapse — eats 60% of a phone screen
- **Layout** has no hamburger menu toggle
- **DashboardPage** uses `grid-cols-4` (stat cards) and `grid-cols-[2fr_1fr]` (alert feed + node status) — both overflow on mobile
- **CamerasPage** uses `grid-cols-4` — cameras stack into a thin unusable column
- **AlertTable** uses an 8-column CSS grid with fixed widths — horizontally overflows, no scroll
- **AnalyticsPage** has side-by-side charts that overflow
- No touch-friendly tap targets (buttons too small, no swipe gestures)
- Header search bar has `min-w-[200px]` — pushes other elements off-screen

Farm managers will check alerts on their phone while walking around the farm. The dashboard must work on screens from 320px (old Android) to 1920px (desktop).

---

## Implementation Plan — 10 Tasks

### Task 1: Collapsible Sidebar with Mobile Drawer

**File:** `dashboard/src/components/Layout/Sidebar.tsx`

**Current:** Fixed `w-[220px]`, always visible, no collapse mechanism.

**Change to:**
- Desktop (≥1024px): Sidebar visible as-is
- Tablet (768-1023px): Sidebar collapses to icon-only (56px wide), expands on hover
- Mobile (<768px): Sidebar hidden, slides in as full-screen overlay drawer from left when hamburger is tapped. Closes on nav item click or backdrop tap.

```tsx
// Key changes:
// 1. Add state: const [mobileOpen, setMobileOpen] = useState(false)
// 2. Desktop: className="hidden lg:flex w-[220px] ..."
// 3. Mobile drawer: className="fixed inset-0 z-50 lg:hidden" with backdrop
// 4. Export setMobileOpen for Header's hamburger button
// 5. Close on NavLink click: onClick={() => setMobileOpen(false)}
```

**Tailwind classes to use:**
```
Desktop sidebar:  hidden lg:flex w-[220px] flex-col h-screen
Mobile drawer:    fixed inset-0 z-50 lg:hidden
Backdrop:         fixed inset-0 bg-black/60 z-40
Drawer panel:     w-[280px] bg-sc-surface h-full z-50 overflow-y-auto
```

---

### Task 2: Hamburger Menu in Header

**File:** `dashboard/src/components/Layout/Header.tsx`

**Current:** No mobile menu toggle. Search bar has fixed `min-w-[200px]`.

**Change to:**
- Add hamburger icon button (visible only on mobile `lg:hidden`)
- Search bar: `min-w-[200px]` → `hidden sm:block sm:min-w-[200px]` (hide on small screens, or collapse to icon)
- Notification bell + avatar: keep visible, reduce padding

```tsx
// Add to left side of header:
<button onClick={onMenuToggle} className="lg:hidden p-2 text-sc-text-dim">
  <Menu size={24} />
</button>
```

---

### Task 3: Layout — Wire Sidebar Toggle

**File:** `dashboard/src/components/Layout/Layout.tsx`

**Current:** Static flex layout, no state management for sidebar.

**Change to:**
```tsx
export default function Layout() {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  
  return (
    <div className="flex h-screen bg-sc-bg overflow-hidden">
      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="flex-1 flex flex-col overflow-hidden">
        <Header onMenuToggle={() => setSidebarOpen(true)} />
        <main className="flex-1 overflow-auto p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
```

Note: `p-6` → `p-4 sm:p-6` gives tighter padding on mobile.

---

### Task 4: DashboardPage — Responsive Grid

**File:** `dashboard/src/pages/DashboardPage.tsx`

**Current:**
```tsx
<div className="grid grid-cols-4 gap-4 mb-6">        // Stat cards
<div className="grid grid-cols-[2fr_1fr] gap-4">     // Alert feed + node status
```

**Change to:**
```tsx
<div className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4 mb-6">
<div className="grid grid-cols-1 lg:grid-cols-[2fr_1fr] gap-4">
```

- Stat cards: 2 columns on mobile, 4 on desktop
- Alert feed + node status: stacked on mobile, side-by-side on desktop
- Siren button section: full width on all screens (already OK)

---

### Task 5: CamerasPage — Responsive Grid

**File:** `dashboard/src/pages/CamerasPage.tsx`

**Current:** `grid-cols-4` — shows 4 cameras per row on all screens.

**Change to:**
```tsx
<div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3">
```

- Mobile: 1 camera per row (full width, large enough to see detail)
- Tablet: 2 per row
- Desktop: 3-4 per row

---

### Task 6: AlertTable — Mobile Card View

**File:** `dashboard/src/components/AlertTable.tsx`

**Current:** 8-column CSS grid that overflows on anything under 1200px.

**Change to:** Dual-mode rendering:
- Desktop (≥1024px): Keep the table grid layout
- Mobile (<1024px): Switch to stacked card layout

```tsx
// Desktop: existing table (hidden on mobile)
<div className="hidden lg:block">
  {/* existing 8-column grid table */}
</div>

// Mobile: card stack
<div className="lg:hidden space-y-2">
  {alerts.map(alert => (
    <div className="bg-sc-surface border border-sc-border rounded-xl p-4">
      <div className="flex items-center justify-between mb-2">
        <PriorityDot priority={alert.priority} />
        <StatusBadge status={alert.status} />
      </div>
      <div className="text-sm font-semibold text-sc-text">
        {alert.detectionClass} — {alert.zoneName}
      </div>
      <div className="text-xs text-sc-text-muted mt-1">
        {alert.cameraId} · {alert.nodeId} · {formatTime(alert.createdAt)}
      </div>
      <div className="flex gap-2 mt-3">
        {alert.status === 'new' && <AckButton />}
        <ViewButton />
      </div>
    </div>
  ))}
</div>
```

---

### Task 7: AnalyticsPage — Responsive Charts

**File:** `dashboard/src/pages/AnalyticsPage.tsx`

**Current:** Side-by-side charts with fixed grids.

**Change to:**
```tsx
// Charts stack on mobile, side-by-side on desktop
<div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

// Stat cards inside analytics ALSO need responsive treatment
// CURRENT:  grid-cols-4
// CHANGE:   grid-cols-2 lg:grid-cols-4
<div className="grid grid-cols-2 lg:grid-cols-4 gap-4">

// Make Recharts responsive
<ResponsiveContainer width="100%" height={250}>
  <BarChart data={data}>...</BarChart>
</ResponsiveContainer>
```

Recharts `ResponsiveContainer` already handles width — just ensure the parent container doesn't have a fixed width.

---

### Task 8: SirenPage — Touch-Friendly Buttons

**File:** `dashboard/src/pages/SirenPage.tsx`

**Current:** Siren trigger button and per-node buttons are reasonably sized.

**Change to:**
- Ensure minimum 48px tap target on all buttons (WCAG touch target guideline)
- Node selection buttons: `grid-cols-2` → `grid-cols-1 sm:grid-cols-2`
- Large trigger button: add `active:scale-95 transition-transform` for tactile feedback

---

### Task 9: Skip — Pages Already Responsive

DevicesPage, WorkersPage, ZonesPage, SettingsPage, and LoginPage already have responsive breakpoints. **No changes needed for these 5 pages.**

---

### Task 10: Global Mobile Polish

**File:** `dashboard/src/index.css` and `dashboard/tailwind.config.js`

1. **Safe area insets** (for phones with notch/home indicator):
```css
main { padding-bottom: env(safe-area-inset-bottom); }
```

2. **Prevent zoom on input focus** (iOS Safari):
```css
input, select, textarea { font-size: 16px; }
```

3. **Smooth scrolling:**
```css
main { -webkit-overflow-scrolling: touch; }
```

4. **Touch-action on interactive elements:**
```css
button, a, [role="button"] { touch-action: manipulation; }
```

5. **Pull-to-refresh prevention** (optional — prevents accidental page reload):
```css
body { overscroll-behavior-y: contain; }
```

---

## Breakpoint Strategy

Use Tailwind's default breakpoints:

| Prefix | Min Width | Target |
|:-------|:----------|:-------|
| (none) | 0px | Small phones (320-639px) |
| `sm:` | 640px | Large phones / small tablets |
| `md:` | 768px | Tablets |
| `lg:` | 1024px | Small desktops / landscape tablets |
| `xl:` | 1280px | Desktops |

**Mobile-first approach:** Write base styles for mobile, then add `sm:`, `md:`, `lg:` overrides for wider screens.

---

## Testing Checklist

After implementation, verify on these screen sizes:

```
□ iPhone SE (375×667)          — smallest common phone
□ iPhone 14 Pro (393×852)      — standard phone
□ iPad Mini (768×1024)         — tablet portrait
□ iPad Pro (1024×1366)         — tablet landscape
□ Desktop (1440×900)           — standard desktop
□ Desktop (1920×1080)          — full HD
```

For each screen, verify:
```
□ Sidebar: hidden on mobile, drawer opens/closes correctly
□ Header: hamburger visible on mobile, search hidden on small screens
□ Dashboard stat cards: 2-col on mobile, 4-col on desktop
□ Alert feed: readable on mobile, no horizontal overflow
□ Camera grid: 1-col on mobile, 4-col on desktop
□ Alert table: card view on mobile, table on desktop
□ Siren button: 48px+ tap target, visual feedback on press
□ Charts: don't overflow, resize correctly
□ No horizontal scrollbar on any page at any width
□ Input fields don't trigger zoom on iOS Safari
```

---

## Non-Breaking Guarantee

Every change uses Tailwind's mobile-first responsive prefixes (`sm:`, `md:`, `lg:`, `xl:`). The existing desktop layout is preserved because responsive classes only ADD behavior at larger breakpoints — they never remove it.

**Proof for each change:**

| Change | Desktop (≥1024px) | Why It's Safe |
|:-------|:-------------------|:-------------|
| Sidebar: `hidden lg:flex` | `lg:flex` shows sidebar | `lg:` activates at 1024px, same as current |
| Hamburger: `lg:hidden` | `lg:hidden` hides button | Desktop never sees the hamburger |
| Stats: `grid-cols-2 lg:grid-cols-4` | `lg:grid-cols-4` = 4 columns | Identical to current `grid-cols-4` |
| AlertTable: `hidden lg:block` | `lg:block` shows table | Desktop renders existing table unchanged |
| Mobile cards: `lg:hidden` | `lg:hidden` hides cards | Desktop never renders card view |
| Padding: `p-4 sm:p-6` | `sm:p-6` at 640px+ | Same as current `p-6` |
| CSS: `env(safe-area-inset-bottom)` | Evaluates to 0px on desktop | No visual change |
| CSS: `touch-action: manipulation` | No effect on mouse | Only affects touchscreens |

**One intentional minor change:** CamerasPage shows 3 columns between 1024-1279px instead of 4. This is actually better (wider cards with more readable text). At 1280px+ (`xl:`), it returns to 4 columns.

## Pages Already Responsive (No Changes Needed)

The agent already added responsive breakpoints to these pages:

| Page | Current Classes | Status |
|:-----|:---------------|:-------|
| DevicesPage | `grid-cols-2 lg:grid-cols-4` | ✅ Already responsive |
| WorkersPage | `grid-cols-1 md:grid-cols-2` | ✅ Already responsive |
| ZonesPage | `grid-cols-1 md:grid-cols-2 lg:grid-cols-3` | ✅ Already responsive |
| SettingsPage | No fixed widths | ✅ Already responsive |
| LoginPage | `max-w-md` centered | ✅ Already responsive |

**These 5 pages need NO changes.** Only DashboardPage, CamerasPage, AnalyticsPage, AlertTable, and the Layout/Sidebar/Header need work.

## Updated File Change Summary

| File | Type | Change | Desktop Impact |
|:-----|:-----|:-------|:---------------|
| `Layout/Sidebar.tsx` | MODIFY | Add `open`/`onClose` props, mobile drawer | None — `lg:flex` preserves desktop |
| `Layout/Header.tsx` | MODIFY | Add `onMenuToggle` prop, hamburger button | None — `lg:hidden` hides hamburger |
| `Layout/Layout.tsx` | MODIFY | Add sidebar state, pass props, responsive padding | None — `sm:p-6` = same as `p-6` |
| `AlertTable.tsx` | MODIFY | Add mobile card view alongside existing table | None — `hidden lg:block` preserves table |
| `DashboardPage.tsx` | MODIFY | `grid-cols-4` → `grid-cols-2 lg:grid-cols-4` | None — `lg:` restores 4 cols |
| `CamerasPage.tsx` | MODIFY | `grid-cols-4` → responsive breakpoints | Minor — 3 cols at 1024-1279px |
| `AnalyticsPage.tsx` | MODIFY | Both grids get responsive breakpoints | None — `lg:` restores desktop layout |
| `SirenPage.tsx` | MODIFY | `min-h-[48px]`, `active:scale-95` | Cosmetic only |
| `index.css` | MODIFY | Safe areas, zoom prevention, touch | None on desktop browsers |
