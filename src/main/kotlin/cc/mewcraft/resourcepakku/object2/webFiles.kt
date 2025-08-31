package cc.mewcraft.resourcepakku.object2

import cc.mewcraft.resourcepakku.logger
import cc.mewcraft.resourcepakku.plugin
import cc.mewcraft.resourcepakku.util.CopyMode
import cc.mewcraft.resourcepakku.util.copyResource

class WebFiles {

    companion object {

        fun copyDefaultWeb() {
            val wrote = copyResource("webserver", plugin.dataDirectory, mode = CopyMode.SKIP)
            if (wrote) {
                logger.warn("Copied default web files to data directory")
            }
        }
    }
}