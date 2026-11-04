package com.tntsuite.addon;

import com.tntsuite.addon.modules.AutoCart;
import com.tntsuite.addon.modules.AutoTnt;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TntSuiteAddon extends MeteorAddon {
    public static final Logger LOG = LoggerFactory.getLogger("TNT Suite");
    public static final Category CATEGORY = new Category("TNT Suite");

    @Override
    public void onInitialize() {
        LOG.info("Loading TNT Suite");
        Modules modules = Modules.get();

        // Core modules
        modules.add(new AutoCart());
        modules.add(new AutoTnt());

        // Future modules go here (subclass SuiteModule, then add one line):
        // modules.add(new AutoCrystal());
        // modules.add(new AutoWeb());
        // modules.add(new AutoTrap());
        // modules.add(new AutoObsidian());
        // modules.add(new AutoAnchor());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.tntsuite.addon";
    }
}
