# Finance Mod Development Notes

## Current Direction

We are pausing backend-heavy feature work for now and shifting priority toward player-facing UI.

Reason: command-only interaction is becoming a blocker. Future features such as K-line charts, stock trading, company management, market depth, and portfolio views need visual screens. Continuing to add backend systems without UI will make later gameplay hard to test and awkward for players.

## Implemented Backend Progress

- Account system:
  - Player balances.
  - Transfers.
  - Frozen balance support for market orders.
  - Transaction history.

- Commodity system:
  - Registered commodities: iron, wheat, coal, steel.
  - Player commodity inventory.
  - Persistent inventory storage.

- P2P market:
  - Buy and sell orders.
  - Order matching.
  - Order cancellation.
  - Trade history.
  - Fixed settlement bug where matched P2P trades could create money instead of spending frozen buyer funds.
  - Added commodity validation and amount overflow guards.

- International market:
  - Renamed player-facing "NPC market" concept to "international market".
  - Added `/market international buy|sell|prices`.
  - Kept `/market npc ...` as a compatibility alias for now.
  - International market now has external restocking and demand balancing instead of only consuming inventory.
  - This prevents prices from only moving upward due to shrinking market inventory.

- Company system:
  - System companies exist and operate daily.
  - Company persistence now saves cash, inventory, type, name, ID, and owner.
  - Company logic was adjusted so companies sell produced goods, not production inputs.
  - Companies keep a raw material reserve before selling.
  - Players can create a first version of a company with `/company create <type> <name>`.
  - Players can inspect their own company with `/company mine`.

- Events and pricing:
  - Market event timers are persisted.
  - Active market events are persisted and reapplied.
  - Price snapshots, momentum, and noise offset are persisted.

## Known Design Issues

- Company creation through commands is not a good final UX.
- Market and company screens need GUI support before adding more complex finance features.
- Internal class names still use `NpcMarketMaker` and `NPC_UUID`; this was intentionally left for compatibility. Player-facing text now says "international market".
- Transaction enum names still include `NPC_BUY` and `NPC_SELL`; these should eventually be migrated or treated as legacy internal names.
- Commodity display names are still mostly IDs in player-facing output.
- There are no automated tests yet for economic invariants.

## Recommended Next Work: UI First

Build a player-facing UI layer before adding stocks/K-line systems.

Suggested first UI screens:

1. Market overview screen
   - Commodity list.
   - Current mid price.
   - Buy/sell quotes.
   - 24h change.
   - Volume.

2. Commodity detail screen
   - Price history.
   - Simple line chart first.
   - Later upgrade to K-line/candlestick chart.
   - International market inventory and recent trades.

3. Company management screen
   - Create company without commands.
   - View company cash, inventory, industry, valuation.
   - Deposit/withdraw funds later.

4. Portfolio/account screen
   - Balance.
   - Frozen balance.
   - Commodity inventory.
   - Recent transactions.

## UI Implementation Notes

- Minecraft Forge 1.20.1 GUI work should likely use server commands/network packets plus client screens.
- Avoid adding stock/K-line backend before the chart display path exists.
- Start with simple screens and buttons, then add chart rendering once data flow is reliable.
- Keep command versions as admin/debug fallbacks, but make normal player workflows GUI-first.

## Last Verified

- `./gradlew.bat compileJava` passed after the backend fixes and company creation work.
