package com.ksptool.ourcraft.server.world;

import com.ksptool.ourcraft.sharedcore.world.SequenceUpdate;
import com.ksptool.ourcraft.sharedcore.world.SharedWorld;

import com.ksptool.ourcraft.sharedcore.world.WorldService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class ServerWorldTimeService extends WorldService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicLong totalActions;

    private final ServerWorld world;

    private final double worldSecondsPerAction;

    private final LocalDateTime startDateTime;

    /**
     * 构造函数
     * @param world World
     * @param previousTotalActions 上一次Action的总次数
     */
    public ServerWorldTimeService(ServerWorld world,long previousTotalActions) {
        this.world = world;
        this.worldSecondsPerAction = world.getTemplate().getWorldSecondsPerAction();
        this.startDateTime = world.getTemplate().getStartDateTime();
        this.totalActions = new AtomicLong(previousTotalActions);
    }

    /**
     * 执行更新
     * @param delta 距离上一Action经过的时间（秒）由SWEU传入
     * @param world 世界
     */
    @Override
    public void action(double delta, SharedWorld world) {
        totalActions.incrementAndGet();
    }

    /**
     * 获取世界时间
     * @return 世界时间(LocalDateTime)
     */
    public LocalDateTime getTime() {
        long passedWorldSeconds = (long) (totalActions.get() * worldSecondsPerAction);
        return startDateTime.plusSeconds(passedWorldSeconds);
    }
    

    /**
     * 获取世界时间字符串
     * @return 世界时间字符串
     */
    public String getTimeString() {
        return getTime().format(DATE_TIME_FORMATTER);
    }

    /**
     * 获取精确的24小时时间（用于渲染器渲染日夜变化）
     * @return 0到23之间的值，表示当前小时数（24小时制）
     */
    public int getTimeOfDay() {
        return getTime().getHour();
    }

    /**
     * 获取总Action次数
     * @return 总Action次数
     */
    public long getTotalActions() {
        return totalActions.get();
    }

    /**
     * 将现实时间转换为Actions(近似值 因为服务端Action不一定均匀)
     * @param amount 时间量
     * @param timeUnit 时间单位
     * @return 总Action次数
     */
    public long getActions(long amount,TimeUnit timeUnit) {
        return (long) (amount * timeUnit.toSeconds(1) * world.getTemplate().getActionPerSecond());
    }

}
