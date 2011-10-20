package haven.scriptengine.providers.providers;

import haven.*;
import haven.scriptengine.providers.providers.*;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static haven.MCache.tilesz;

/**
 * Created by IntelliJ IDEA.
 * Player: Vlad.Rassokhin@gmail.com
 * Date: 10.01.11
 * Time: 19:15
 */
@SuppressWarnings({"UnusedDeclaration"})
public class Player implements ProgressBar.ProgressListener, IMeter.Listener {

    private static Coord position;

    public static Gob getGob() {
        final Glob glob = CustomConfig.getGlob();
        synchronized (glob.oc) {
            return glob.oc.getgob(Player.getId());
        }
    }

    public static int getId() {
        return CustomConfig.getPlayerId();
    }

    public static int getStamina() {
        return stamina;
    }

    public static int getAuthority() {
        return authorityNow;
    }

    public static int getAuthorityMax() {
        return authorityMax;
    }

    public static int getHPHard() {
        return hpHard;
    }

    public static int getHPSoft() {
        return hpSoft;
    }

    public static int getHP() {
        return hp;
    }

    public static int getHappy() {
        return happy;
    }

    public static int getHappyTowards() {
        return happyTowards;
    }

    public static int getHungry() {
        return hungry;
    }

    public static int getSpeed() {
        return UI.speedget.get().getMax();
    }

    public static int getMaxSpeed() {
        return UI.speedget.get().getMax();
    }

    public static boolean setSpeed(final int speed) {
        return UI.speedget.get().changeSpeed(speed);
    }

    public static void iMeterGenerated(@NotNull final IMeter meter, @NotNull final String resName) {
        newMeter(meter, resName);
    }

    private static void meterUp(@NotNull final IMeter meter, @NotNull final String tooltip) {
        try {
            if (meter == hpMeter) updateHp(tooltip);
            if (meter == energyMeter) updateEnergy(tooltip);
            if (meter == happyMeter) updateHappy(tooltip);
            if (meter == hungryMeter) updateHungry(tooltip);
            if (meter == authorityMeter) updateAuthority(tooltip);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void newMeter(@NotNull final IMeter meter, @NotNull final String resName) {
        if (resName.equals("gfx/hud/meter/hp")) //HP meter
            hpMeter = meter;
        else if (resName.equals("gfx/hud/meter/nrj")) // Energy Meter
            energyMeter = meter;
        else if (resName.equals("gfx/hud/meter/hngr")) // Hungry Meter
            hungryMeter = meter;
        else if (resName.equals("gfx/hud/meter/happy")) // Happyness Meter
            happyMeter = meter;
        else if (resName.equals("gfx/hud/meter/auth")) // Authority Meter
            authorityMeter = meter;
        else
            System.err.println("Unexpected IMeter with imagename=" + resName);
    }

    private static void updateAuthority(@NotNull final String tooltip) {
        final Matcher makeMatch = intsOnly.matcher(tooltip);
        makeMatch.find();
        authorityNow = Integer.parseInt(makeMatch.group());
        makeMatch.find();
        authorityMax = Integer.parseInt(makeMatch.group());
    }

    private static void updateHungry(@NotNull final String tooltip) {
        final Matcher makeMatch = intsOnly.matcher(tooltip);
        makeMatch.find();
        makeMatch.find();
        hungry = Integer.parseInt(makeMatch.group());
    }

    private static void updateHappy(@NotNull final String tooltip) {
        final Matcher makeMatch = intsOnly.matcher(tooltip);
        makeMatch.find();
        happy = Integer.parseInt(makeMatch.group());
        happyTowards = (makeMatch.find()) ? Integer.parseInt(makeMatch.group()) : 0;
        if (tooltip.startsWith("Neutral")) {
            happyTowards = happy;
            happy = 0;
        }
        if (tooltip.startsWith("Un")) happy *= -1;
    }

    private static void updateEnergy(final String tooltip) {
        final Matcher makeMatch = intsOnly.matcher(tooltip);
        makeMatch.find();
        stamina = Integer.parseInt(makeMatch.group());
    }


    public static void updateHp(final String tooltip) {
        final Matcher makeMatch = intsOnly.matcher(tooltip);
        makeMatch.find();
        hp = Integer.parseInt(makeMatch.group());
        makeMatch.find();
        hpSoft = Integer.parseInt(makeMatch.group());
        makeMatch.find();
        hpHard = Integer.parseInt(makeMatch.group());
    }

    static private IMeter hpMeter;
    static private IMeter energyMeter;
    static private IMeter happyMeter;
    static private IMeter hungryMeter;
    static private IMeter authorityMeter;

    static private int id = -1;
    static private int stamina = -1;
    static private int hp = -1;
    static private int hpSoft = -1;
    static private int hpHard = -1;
    static private int hungry = -1;
    static private int authorityNow = -1;
    static private int authorityMax = -1;
    static private int happy = -1;
    static private int happyTowards = -1;

    static private int progress = -1; // [0-100] or -1 if not in progress

    static final Pattern intsOnly = Pattern.compile("[-]?\\d+");

    public static void updateProgress(final int p) {
        progress = p;
    }

    public static int getProgress() {
        return progress;
    }

    public static boolean isInProgress() {
        return progress >= 0;
    }

    public static Coord getPosition() {
        final Gob pl = getGob();
        if (pl != null) {
            return pl.getc();
        } else {
            return null;
        }
    }

    public static void sayAreaChat(final String message) {
        // TODO: say something into area chat
    }


    // для начала двигаться к указанному объекту с оффсетом
    public static void move(final int objectId, final int offX, final int offY) {
        final Gob gob = haven.scriptengine.providers.providers.MapProvider.getGob(objectId);
        if (gob == null) {
            return;
        }
        Coord oc = gob.getc();
        if (oc == null) {
            return;
        }
        final int btn = 1; // левой кнопкой щелкаем
        final int modflags = 0; // никаких клавиш не держим
        oc = oc.add(offX, offY);
        haven.scriptengine.providers.providers.MapProvider.getMV().wdgmsg("click", haven.scriptengine.providers.providers.MapProvider.getCenterR(), oc, btn, modflags, objectId, oc);
    }

    public static void moveStep(final int x, final int y) {
        final Coord pos = getPosition();
        if (pos == null) {
            return;
        }
        final int button = 1; // левой кнопкой щелкаем
        final int modflags = 0; // никаких клавиш не держим
        Coord mc = MapView.tilify(pos);
        final Coord offset = tilesz.mul(x, y);
        mc = mc.add(offset);
        haven.scriptengine.providers.providers.MapProvider.getMV().wdgmsg("click", haven.scriptengine.providers.providers.MapProvider.getCenterR(), mc, button, modflags);
    }

    public static boolean isMoving() {
        return haven.scriptengine.providers.providers.MapProvider.getMV().player_moving;
    }

    @Override
    public void onChanged(final int percents) {
        updateProgress(percents);
    }

    @Override
    public void onFinished() {
        updateProgress(-1);
    }

    @Override
    public void onStarted() {
        updateProgress(0);
    }

    @Override
    public void onIMeterAdded(IMeter meter, String resourceName) {

    }

    @Override
    public void onIMeterRemoved(IMeter meter) {
    }

    @Override
    public void onIMeterUpdated(IMeter meter, String tooltip) {
        meterUp(meter, tooltip);
    }
}