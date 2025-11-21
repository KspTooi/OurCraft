package com.ksptool.ourcraft.sharedcore;

public enum WorldTemplates {

    DEFAULT_TEMPLATE("ourcraft:earth_like");

    private String templateRegId;

    WorldTemplates(String templateId){
        this.templateRegId = templateId;
    }

    public String getRegId() {
        return templateRegId;
    }

}
