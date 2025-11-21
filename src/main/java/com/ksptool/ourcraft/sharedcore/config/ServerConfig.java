package com.ksptool.ourcraft.sharedcore.config;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * 服务器配置类 用于存储服务器配置信息
 */
@Slf4j
@Getter@Setter
public class ServerConfig {

    //服务器名称
    private String serverName;

    //绑定地址
    private String bindAddress;

    //端口
    private int port;

    //存档名称
    private String saveName;

    //主世界名称
    private String mainWorldName;


    /**
     * 获取服务器配置实例
     * 尝试从运行目录下获取 server_config.yml 文件，如果文件不存在，则写入默认配置
     * @return 服务器配置实例
     */
    public static ServerConfig getInstance() {

        var yaml = new Yaml();
        var defaultConfig = new ServerConfig();
        defaultConfig.setServerName("A OurCraft Server");
        defaultConfig.setBindAddress("0.0.0.0");
        defaultConfig.setPort(25564);
        defaultConfig.setSaveName("our_craft");
        defaultConfig.setMainWorldName("earth_like");

        Path configPath = Paths.get("server_config.yml");

        //创建默认配置文件
        if (!Files.exists(configPath)) {

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            var dumperYaml = new Yaml(options);

            try {
                Files.createFile(configPath);

                var map = new HashMap<String, Object>();
                map.put("serverName", defaultConfig.getServerName());
                map.put("bindAddress", defaultConfig.getBindAddress());
                map.put("port", defaultConfig.getPort());
                map.put("saveName", defaultConfig.getSaveName());
                map.put("mainWorldName", defaultConfig.getMainWorldName());
                dumperYaml.dump(map, new FileWriter(configPath.toFile()));

                log.info("Created default server config file: {}", configPath.toAbsolutePath());

            } catch (IOException e) {
                log.error("Failed to create default server config", e);
                return defaultConfig;
            }
        }

        //读取配置文件
        try {
            var config = yaml.loadAs(new FileReader(configPath.toFile()), ServerConfig.class);

            log.info("Loaded server config from file: {}", configPath.toAbsolutePath());

            //校验服务器名称
            if(StringUtils.isBlank(config.getServerName())){
                log.error("Server name is blank, using default name");
                config.setServerName(defaultConfig.getServerName());
            }

            //校验绑定地址是否为合法的IP地址
            if(InetAddress.getByName(config.getBindAddress()) == null){
                log.error("Bind address is not a valid IP address, using default address");
                config.setBindAddress(defaultConfig.getBindAddress());
            }

            //校验端口是否为合法的端口
            if(config.getPort() < 1 || config.getPort() > 65535){
                log.error("Port is not a valid port, using default port");
                config.setPort(defaultConfig.getPort());
            }

            //校验存档名称
            if(StringUtils.isBlank(config.getSaveName())){
                log.error("Save name is blank, using default save name");
                config.setSaveName(defaultConfig.getSaveName());
            }

            //校验主世界名称
            if(StringUtils.isBlank(config.getMainWorldName())){
                log.error("Main world name is blank, using default main world name");
                config.setMainWorldName(defaultConfig.getMainWorldName());
            }

            return config;
        } catch (IOException e) {
            log.error("Failed to read server config", e);
            return defaultConfig;
        }
        
    }

}
