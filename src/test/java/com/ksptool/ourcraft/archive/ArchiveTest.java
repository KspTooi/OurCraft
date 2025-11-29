package com.ksptool.ourcraft.archive;

import com.ksptool.ourcraft.server.archive.ArchiveManager;
import com.ksptool.ourcraft.server.archive.model.ArchiveVo;
import org.junit.jupiter.api.Test;


public class ArchiveTest {


    @Test
    public void testCreateArchive() {

        ArchiveManager archiveManager = new ArchiveManager();
        //archiveManager.createArchive("test_archive");

    }

    @Test
    public void testLoadArchive() {

        ArchiveManager archiveManager = new ArchiveManager();
        ArchiveVo testArchive = archiveManager.loadArchive("test_archive");
        System.out.println(testArchive);
    }



}
