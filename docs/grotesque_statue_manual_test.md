# Grotesque Statue manual test

Use a dedicated or integrated server with Curios and Goety loaded. Run tests as a
non-creative, non-spectator player in a dimension where Illager Treachery can
resolve. Use `/goetyarkham illager_treachery trigger` for every test that needs a
real event; a rejected trigger must never consume statue souls.

The item uses Goety's standard `Souls` and `Max Souls` item NBT. For focused
tests, create charged stacks with commands such as:

```mcfunction
/give @s goetyarkham:grotesque_statue{Souls:5000,"Max Souls":5000}
```

1. Run `/give @s goetyarkham:grotesque_statue` and confirm the item appears with
   `0/5000` Soul Energy and the localized effect tooltip.
2. Confirm Curios accepts the item in `necklace` (护符), rejects every other
   Curios slot, and that the existing necklace slot count is unchanged.
3. Keep a charged statue in the normal inventory and force an event. Confirm the
   player resolves an encounter and the stack remains unchanged.
4. Equip a 5000-soul statue and force five separate events. Confirm the player
   is immune and the stack reads 4000, 3000, 2000, 1000, then 0. Confirm one
   action-bar message and one local sound per event.
5. Force a sixth event. Confirm it resolves normally and the statue remains at
   0. Repeat with 999 souls and confirm no deduction or failure message.
6. Equip two statues. Verify `[999, 1000]` spends from the second; `[1000, 5000]`
   spends only from the first; `[500, 500]` does not activate or combine energy.
7. Enable an encounter that requests extra draws and force one event. Confirm no
   encounter or chaos check runs for the protected player and exactly 1000 total
   souls are spent.
8. With two online players, equip and charge only one player's statue. Force one
   event and confirm only that player is immune; the other resolves normally.
9. Exercise daily probability failure, cooldown rejection, an unmet trigger
   condition, and an event-lock rejection. Confirm none consumes souls. Then
   test daily success/six-day guarantee, ritual, raid, Calamity Hunt, and the
   force command; every entry point that starts a real event must protect.
10. Move a partially charged statue through inventory, dropped-item pickup, and
    a container. Relog, cross dimensions, die/respawn under the server's Curios
    drop/keep rules, and restart the server. Confirm the surviving stack retains
    the exact `Souls` value and always reports a 5000 maximum.
11. Repeat a trigger while watching the item NBT. Confirm a single global event
    can never deduct 2000, including encounters with extra draws.

For multiplayer and trigger-source coverage, also confirm global cooldown,
valid-day reset, guarantee progress, and event-lock state advance exactly as
they do without the statue, even when every participant is protected.
