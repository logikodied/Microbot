package net.runelite.client.plugins.microbot.winecollector;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kaas.pyrefox.helpers.BankHelper;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class WineCollectorScript extends Script {

    public static final String version = "1.1.0";
    private static final String WINE_NAME = "Eclipse red";
    private static final int[] DANGEROUS_WORLDS = {
            324, 325, 337, 392, 393, 417, 318, 319, 370, 471, 474, 475
    };

    private WorldPoint wineArea;

    private enum State { COLLECTING, BANKING }

    public boolean run(WineCollectorConfig config) {
        if (!Microbot.isLoggedIn()) return false;

        WorldPoint initialLocation = Rs2Player.getWorldLocation();
        wineArea = (initialLocation.getPlane() == 2)
                ? initialLocation
                : new WorldPoint(1556, 3034, 2);

        Rs2Antiban.resetAntibanSettings();
        Microbot.log("WineCollector started at: " + initialLocation);
        Microbot.log("Wine collection area: " + wineArea);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                if (!isReady()) return;

                Rs2Antiban.takeMicroBreakByChance();

                switch (getState()) {
                    case BANKING:
                        handleBanking();
                        break;
                    case COLLECTING:
                        handleCollection();
                        break;
                }

                Rs2Antiban.actionCooldown();
                Rs2Random.waitEx(100, 600);

            } catch (Exception ex) {
                Microbot.log("Error: " + ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private State getState() {
        return Rs2Inventory.isFull() ? State.BANKING : State.COLLECTING;
    }

    private boolean isReady() {
        return !Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Microbot.pauseAllScripts;
    }

    private void handleCollection() {
        Microbot.status = "Collecting wine";

        if (!isOnCorrectPlane()) {
            Microbot.status = "Navigating to wine floor";
            walkToWineArea();
            return;
        }

        Rs2Random.waitEx(900, 300);

        if (Rs2GroundItem.exists(WINE_NAME, 10)) {
            Microbot.status = "Picking up wine";
            Rs2Random.waitEx(550, 250);
            Rs2GroundItem.pickup(WINE_NAME, 10);
            sleepUntil(() -> Rs2Inventory.hasItem(WINE_NAME), 3000);
            Rs2Random.waitEx(850, 350);
        } else {
            Microbot.status = "No wine found - hopping worlds";
            Rs2Random.waitEx(3200, 1500);
            attemptWorldHop();
        }
    }

    private void handleBanking() {
        Microbot.status = "Banking items";

        if (BankHelper.walkToAndOpenBank(BankLocation.HUNTERS_GUILD)) {
            Rs2Random.waitEx(800, 400);
            Rs2Bank.depositAll();
            Rs2Bank.closeBank();
            Microbot.status = "Returning to wine area";
            walkToWineArea();
        }
    }

    private void walkToWineArea() {
        Rs2Walker.walkTo(wineArea);
        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 2, 10000);
        Rs2Random.waitEx(500, 300);
    }

    private boolean isOnCorrectPlane() {
        return Rs2Player.getWorldLocation().getPlane() == 2;
    }

    private void attemptWorldHop() {
        int attempts = 0;
        while (attempts++ < 10) {
            int world = Login.getRandomWorld(Rs2Player.isMember());
            if (isSafeWorld(world) && Microbot.hopToWorld(world)) {
                Microbot.status = "Hopped to world: " + world;
                Rs2Random.waitEx(2750, 750);
                return;
            }
            Rs2Random.waitEx(600, 200);
        }
    }

    private boolean isSafeWorld(int world) {
        for (int w : DANGEROUS_WORLDS) {
            if (w == world) return false;
        }
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
