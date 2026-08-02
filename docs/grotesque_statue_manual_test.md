# Grotesque Statue manual test

Use a dedicated or integrated server with Curios and Goety loaded. Run tests as a
non-creative, non-spectator player in a dimension where Illager Treachery can
resolve. Use `/goetyarkham illager_treachery trigger` for every test that needs a
real event; a rejected trigger must never consume statue souls.

The item uses Goety's standard `Souls` and `Max Souls` item NBT. Its inventory
bar and soul tooltip always describe that stack, while Illager Treachery pays a
fixed 1000 souls from the unified pool managed by `SoulEnergyPoolService`.
For focused tests, create charged stacks with commands such as:

```mcfunction
/give @s goetyarkham:grotesque_statue{Souls:5000,"Max Souls":5000}
```

1. Run `/give @s goetyarkham:grotesque_statue` and confirm the item appears with
   `0/5000` Soul Energy and the localized pool-payment effect tooltip. Set the
   stack to 0, 1000, 2500 and 5000 souls; confirm its bar is respectively empty,
   about 20%, about 50% and full, with the same colors as Goety's soul Totems.
   Confirm the item never gains vanilla `Damage`, durability behavior, repair,
   Mending or Unbreaking behavior.
2. Confirm Curios accepts the item in Goety's existing `charm` (护符) slot,
   rejects `necklace` (项链) and every other Curios slot, and does not change
   the existing charm slot count.
3. Keep a statue in the normal inventory with a unified pool of at least 5000
   and force an event. Confirm the player resolves an encounter and the pool is
   not charged because the statue is not equipped.
4. Equip a 0-soul statue while another valid pool source provides 5000 souls.
   Force an event and confirm immunity, a pool total of 4000, exactly one action-
   bar message and one local sound. Confirm the statue bar stays empty if the
   unified removal order paid from another source.
5. Test unified pool totals of 999 and exactly 1000. Confirm 999 neither protects
   nor changes any source; exactly 1000 protects and leaves the pool at zero.
6. Equip two statues and provide at least 1000 total pool soul. Confirm a single
   event charges exactly 1000 and grants only one protection. Statue stack
   charge is not used to select which equipped statue activates.
7. Enable an encounter that requests extra draws and force one event. Confirm no
   encounter or chaos check runs for the protected player and exactly 1000 total
   pool souls are spent.
8. With two online players, equip and charge only one player's statue. Force one
   event and confirm only that player is immune; the other resolves normally.
9. Exercise daily probability failure, cooldown rejection, an unmet trigger
   condition, and an event-lock rejection. Confirm none consumes souls. Then
   test daily success/six-day guarantee, ritual, raid, Calamity Hunt, and the
   force command; every entry point that starts a real event must protect.
10. Give the player a 50% Goety soul discount and force a protected event.
    Confirm the unified pool still loses exactly 1000, never 500.
11. Arrange multiple valid soul sources and confirm the source order follows
    `SoulPoolOperations`. If it reaches the statue, its own bar falls by the
    amount actually removed; if another source pays, the statue bar is unchanged.
12. Move a partially charged statue through inventory, dropped-item pickup, and
    a container. Relog, cross dimensions, die/respawn under the server's Curios
    drop/keep rules, and restart the server. Confirm the surviving stack retains
    the exact `Souls` value and always reports a 5000 maximum.
13. Repeat a trigger while watching every pool source. Confirm a single global
    event can never deduct 2000, including encounters with extra draws.

For multiplayer and trigger-source coverage, also confirm global cooldown,
valid-day reset, guarantee progress, and event-lock state advance exactly as
they do without the statue, even when every participant is protected.
