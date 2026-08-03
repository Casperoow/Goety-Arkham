# Holy Rosary manual verification

Use an integrated or dedicated server with Goety, Curios, and Goety: Arkham
loaded. The automated GameTest verifies registry wiring, the `hands` restriction,
tooltip formatting, both bonuses, two-slot stacking, repeated refreshes, and the
sanity clamp. Use this checklist for UI and player-lifecycle behavior.

1. Open the **Goety: Arkham** creative tab and confirm **Holy Rosary** is present.
   Switch to Chinese and confirm the tab is **诡厄巫法：阿卡姆** and the item is
   **圣玫瑰珠**.
2. Confirm the tooltip is exactly three lines. The shared `When worn:` / `佩戴时：`
   heading must be yellow; the two gray lines must read `+2 Max Sanity`, `+1 Will`
   or `+2 最大理智`, `+1 意志`.
3. Run `/give @s goetyarkham:holy_rosary 2`. Confirm each stack has a maximum size
   of one, can enter either `hands` slot, and is rejected by every other Curios
   slot.
4. Before equipping, record `/goetyarkham sanity status` and
   `/goetyarkham stats get @s willpower`. Equip one rosary and confirm effective
   maximum sanity rises by 2 and final Will rises by 1 without changing base Will.
   Equip the second rosary in the other `hands` slot and confirm totals of +4 and
   +2. Repeatedly swap and re-equip them; no extra bonus may accumulate.
5. With both rosaries equipped, fill current sanity to the effective maximum.
   Remove one and then both. Confirm each removal immediately lowers the effective
   maximum by 2, lowers final Will by 1, and clamps current sanity down without
   changing permanent maximum loss or restoring sanity later.
6. Repeat the recorded comparisons after traveling through a dimension portal,
   dying and respawning under the server's Curios drop/keep rules, and leaving and
   rejoining the server. Equipped bonuses must return exactly once; absent items
   must leave no equipment bonus or base-stat change.
