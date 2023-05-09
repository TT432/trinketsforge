package io.github.tt432.trinketsforge;

import dev.emi.trinkets.TrinketsMain;
import io.github.tt432.trinketsforge.net.TrinketsNetHandler;
import net.minecraftforge.fml.common.Mod;


@Mod(Trinketsforge.MOD_ID)
public class Trinketsforge {
    public static final String MOD_ID = "trinketsforge";

    public Trinketsforge() {
        TrinketsMain.onInitialize();
        TrinketsNetHandler.init();
    }
}
