package com.hwanil.illegalcardetector.judgment;

import com.hwanil.illegalcardetector.common.ViolationType;

/**
 * 유형별 개수 집계 결과 한 줄 (JudgmentRepository.countByLabel()의 결과)
 */
public record LabelCount(ViolationType label, long count) {
}
