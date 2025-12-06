package com.ksptool.ourcraft.server;

import com.ksptool.ourcraft.sharedcore.GlobalService;
import com.ksptool.ourcraft.sharedcore.config.ServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * 服务器配置服务
 * 负责读取和写入服务器配置
 */
@Slf4j
public class ServerConfigService implements GlobalService {

    //缓存的服务器配置
    private ServerConfig config;

    //服务器实例
    private final OurCraftServer server;

    public ServerConfigService(OurCraftServer server) {
        this.server = server;
    }
    
    /**
     * 读取服务器配置
     * @return 服务器配置
     */
    public ServerConfig read() {
        if(config == null){
            config = createOrReadConfigInternal();
        }
        return config;
    }


    /**
     * 重新加载服务器配置
     * @return 服务器配置
     */
    public ServerConfig reload() {
        config = createOrReadConfigInternal();
        return config;
    }

    /**
     * 获取服务器配置实例
     * 尝试从运行目录下获取 server_config.yml 文件，如果文件不存在，则写入默认配置
     * @return 服务器配置实例
     */
    private ServerConfig createOrReadConfigInternal() {

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


                log.info("创建默认服务器配置文件: {}", configPath.toAbsolutePath());

            } catch (IOException e) {
                log.error("创建默认服务器配置文件失败", e);
                return defaultConfig;
            }
        }

        //读取配置文件
        try {
            var config = yaml.loadAs(new FileReader(configPath.toFile()), ServerConfig.class);

            log.info("加载服务器配置文件: {}", configPath.toAbsolutePath());

            //校验服务器名称
            if(StringUtils.isBlank(config.getServerName())){
                log.error("服务器名称未配置,使用默认名称");
                config.setServerName(defaultConfig.getServerName());
            }

            //校验绑定地址是否为合法的IP地址
            if(InetAddress.getByName(config.getBindAddress()) == null){
                log.error("绑定地址不是有效的IP地址,使用默认地址");
                config.setBindAddress(defaultConfig.getBindAddress());
            }

            //校验端口是否为合法的端口
            if(config.getPort() < 1 || config.getPort() > 65535){
                log.error("端口不是有效的端口,使用默认端口");
                config.setPort(defaultConfig.getPort());
            }

            //校验存档名称
            if(StringUtils.isBlank(config.getSaveName())){
                log.error("存档名称未配置,使用默认存档名称");
                config.setSaveName(defaultConfig.getSaveName());
            }

            //校验主世界名称
            if(StringUtils.isBlank(config.getMainWorldName())){
                log.error("主世界名称未配置,使用默认主世界名称");
                config.setMainWorldName(defaultConfig.getMainWorldName());
            }

            return config;
        } catch (IOException e) {
            log.error("读取服务器配置文件失败", e);
            return defaultConfig;
        }

    }


}
