# Phantom's AE2 Tweaks

Client-side addon with fixes and improvements for Applied Energistics 2, mostly about its EMI integration. Each
improvement stands on its own.

## Features

### Network contents, without the cost

EMI rebuilds the craftables list it derives from a terminal whenever the inventory it was given differs from the last
one, and with the network contents in that inventory a single rebuild costs 250-315 ms on a network with 40,000 item
types. EMI asks for it at least every 400 ms, and every 50 ms while the craftables sidebar is showing, so a terminal on
a large network that receives items drops to single-digit fps.

This keeps that list up to date incrementally instead.

Craftoria, 40,565 item types, crafting terminal open with the craftables sidebar showing, one network key changed every
500 ms, client profiled with spark:

| Render thread, 40 s window | AE2 alone | with this mod |
|---|---|---|
| EMI + AE2 share | 49.5% | 3.1% |
| craftables rebuild | 23.8 s (68.1%) | 6 ms (0.02%) |
| fps during the one change per 500 ms | 1-6 | 20-30 |

Measured on one machine and pack, so the absolute numbers may differ elsewhere. But overall performance should be a lot
better.

How it does that:

- AE2's own EMI recipe handlers are patched, so the inventory they hand to EMI is cached per terminal and backed by the
  whole network, and their craftability check skips the per-recipe allocations EMI's generic one makes. Because these
  are the handlers AE2 uses, every terminal that gets one is covered - the crafting, pattern and wireless terminals, and
  the wireless ones other addons add by registering an AE2 handler for their own menu type.
- A small mixin on AE2's client repository (`appeng.client.gui.me.common.Repo`) adds the change notification this needs,
  since AE2 has none: a revision counter and the set of keys that changed since the last poll. It also drops
  incremental updates that carry no entries, for which AE2 would rebuild and re-sort its whole view.
- Craftability checks, which EMI runs for every candidate recipe during a rebuild, are answered without the per-recipe
  map and list allocations that make EMI's own implementation slow, and recipes no registered handler supports are
  skipped before EMI's per-recipe metadata calls.
- The repository caches which keys are craftable, which the terminal's craftable filter and pattern encoding otherwise
  collect by scanning every entry.

The list it produces is identical to EMI's own, amounts included; only the ordering of entries that share an EMI stack
index can differ after an incremental update. The network inventory is always exposed to EMI: AE2's
`provideNetworkInventoryToEmi` option is forced on.

### Creative order for the mod sort

AE2's mod order groups the terminal by mod. This makes it list items in the order the creative inventory does (the order
of its search tab), including variants such as potions and enchanted books, with fluids following the items.

It takes the place of the mod order rather than adding a fourth one: AE2's sort orders are an enum that the terminal
cycles through and other screens switch on, so extending it breaks every switch over it. Set `creativeSort = false` in
`config/phantoms_ae2_tweaks-client.toml` to get the mod order back.

A view of 40,000 types sorts in a few milliseconds, where the name order takes over 100 ms.

### Craft hotkeys

EMI's craft hotkeys move a recipe into the crafting grid and then expect the result to be taken, which AE2 only does
when the result slot is clicked, so the grid filled up and nothing was crafted. The mod sends that click with the action
matching EMI's destination and amount: a single craft or a full stack onto the cursor, or a full craft's worth or as
much as fits into the inventory. AE2 has no single-craft-to-inventory action, so the server side of the mod performs
that one: the result is crafted on the server and put into the player's inventory in one step.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.169 or newer
- Applied Energistics 2 19.2.15 or newer
- EMI 1.1.22 or newer

The EMI work runs on the client. "Craft one to inventory" is carried out by the server side of the mod, since the
craft has to be taken there, so the mod belongs on the server as well: without it, that hotkey falls back to crafting a
single result onto the cursor.

Terminals using AE2's own EMI handler are covered entirely, including the wireless ones other addons add by reusing it.
A terminal whose mod ships an EMI handler of its own keeps building its inventory that way, but its craftables list is
still kept incrementally. AE2's client repository, its EMI handlers and EMI's `EmiFavorite`/`EmiStackList` are
internals, as they are for AE2's own EMI integration: minimum versions are declared, but an update to any of them can
still require changes here.
