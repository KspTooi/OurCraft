package com.ksptool.ourcraft.clientj.service;

import com.jme3.font.BitmapFont;
import com.ksptool.ourcraft.clientj.OurCraftClientJ;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.TextField;
import com.simsilica.lemur.style.BaseStyles;
import com.simsilica.lemur.style.Styles;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GuiService {

    public static final float REFERENCE_WIDTH = 800F;
    public static final float REFERENCE_HEIGHT = 600F;

    private final OurCraftClientJ client;

    public GuiService(OurCraftClientJ client) {
        this.client = client;

        GuiGlobals.initialize(client);
        BaseStyles.loadGlassStyle();
        GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");

        //应用字体
        BitmapFont customFont = GlobalFontService.getFont();
        Styles styles = GuiGlobals.getInstance().getStyles();

        if (GlobalFontService.getFont() != null) {
            styles.getSelector(Label.ELEMENT_ID, "glass").set("font", customFont);
            styles.getSelector(Button.ELEMENT_ID, "glass").set("font", customFont);
            styles.getSelector(TextField.ELEMENT_ID, "glass").set("font", customFont);
        }

        log.info("GuiService: Gui initialized");
    }


}
