package com.ksptool.ourcraft.server.manager;

import org.apache.commons.lang3.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServerWorldManager {

    private String saveName;


    public ServerWorldManager(String saveName){

        if(StringUtils.isBlank(saveName)){
            log.error("Save name cannot be blank");
            throw new IllegalArgumentException("Save name cannot be blank");
        }

        this.saveName = saveName;
    }


    public void loadWorld(String worldName) {

    }

    public void saveWorld(String worldName) {

    }

    public void unloadWorldAndSave(String worldName) {

    }


    public void createWorld(String worldName, String worldTemplateRegId) {

        

    }


}
