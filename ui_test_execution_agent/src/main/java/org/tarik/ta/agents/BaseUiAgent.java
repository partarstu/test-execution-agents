package org.tarik.ta.agents;

import org.tarik.ta.core.agents.BaseAiAgent;
import org.tarik.ta.core.dto.FinalResult;
import org.tarik.ta.utils.CommonUtils;

import java.awt.image.BufferedImage;

public interface BaseUiAgent<T extends FinalResult<T>> extends BaseAiAgent<T> {
    @Override
    default BufferedImage captureErrorScreenshot() {
        return CommonUtils.captureScreen();
    }
}
