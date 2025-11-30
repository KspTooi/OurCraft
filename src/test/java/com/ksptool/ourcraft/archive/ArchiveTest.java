package com.ksptool.ourcraft.archive;

import com.ksptool.ourcraft.server.archive.ArchiveService;
import com.ksptool.ourcraft.server.archive.model.ArchiveVo;
import org.junit.jupiter.api.Test;


public class ArchiveTest {


    @Test
    public void testCreateArchive() {

        ArchiveService archiveService = new ArchiveService();
        //archiveManager.createArchive("test_archive");

    }

    @Test
    public void testLoadArchive() {

        ArchiveService archiveService = new ArchiveService();
        ArchiveVo testArchive = archiveService.loadArchive("test_archive");
        System.out.println(testArchive);
    }



}
