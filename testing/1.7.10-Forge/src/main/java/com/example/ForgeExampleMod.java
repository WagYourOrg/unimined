package com.example;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = ForgeExampleMod.MODID, name = "Forge Example Mod", version = "1.0.0")
public class ForgeExampleMod {
    public static final String MODID = "forge-example-mod";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ForgeExampleMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void preInit(FMLInitializationEvent event) {
        LOGGER.info("Hello from Minecraft!");
    }

    @SubscribeEvent
    public void onMainMenu(GuiOpenEvent event) {
        if (event.gui instanceof GuiMainMenu) {
            LOGGER.info("This is the main menu!");
        }
    }

}