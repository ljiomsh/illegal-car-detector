package com.hwanil.illegalcardetector.analyze;

/**
 * 비전 AI에 보내는 글 (어느 AI를 쓰든 같은 기준으로 판별하도록 한곳에 모아 둬요)
 */
final class VisionPrompts {

	private VisionPrompts() {
	}

	/**
	 * 판별 기준 (system 메시지)의 틀. AI는 사진에서 보이는 것을 checks에 정해진 값으로 적고,
	 * 서버(AnalysisParser)가 checks로 같은 규칙을 한 번 더 적용해서 최종 유형을 정해요.
	 * checks의 키·값을 바꾸면 AnalysisParser의 규칙과 프론트 constants/aiChecks.js도 같이 바꿔 주세요.
	 * {vehicle}, {crosswalk}, {sidewalk} 자리는 모델마다 다른 설명이 들어가요. (아래 SYSTEM_FOR_*)
	 */
	private static final String TEMPLATE = """
			너는 사진으로 불법 주정차를 판별하는 도우미야. 아래 순서대로 사진을 꼼꼼히 확인하고, 확인한 것을 checks에 정해진 값으로만 적어.
			사진에 실제로 보이는 것만 근거로 삼고, 보이지 않는 것은 추측하지 마.

			1. vehicle: 사진의 중심이 되는 자동차
			{vehicle}
			   - "none": 자동차가 없거나, 멀리 작게만 보여서 알아볼 수 없음
			   - "unclear": 너무 어둡거나 흐리거나 가려져서 판단할 수 없음
			2. crosswalk: 횡단보도(차도 위의 흰색·노란색 줄무늬)
			{crosswalk}
			   - "none": 횡단보도가 안 보이거나, 차에서 10m보다 멀리 있음
			3. sidewalk: 보도(인도)
			{sidewalk}
			4. hydrant: 소화전
			   - "near": 소화전(빨간색·노란색 기둥)이나 소화전 표시(빨간 연석, '소화전' 글자)가 차에서 약 5m(차 한 대 길이) 안, 같은 쪽 길가에 있음
			   - "far": 소화전이 보이지만 차에서 차 한 대 길이보다 멀거나, 길 건너편·울타리 건너편에 있음
			   - "none": 소화전이 보이지 않음
			5. noParking: 1~4가 아니어도 사진에서 주정차 금지를 알 수 있는 것
			   - "yellow_line": 차 옆 도로 가장자리에 노란 실선이나 노란 겹선이 있음
			   - "intersection": 차가 교차로 모퉁이에서 약 5m 안에 있음
			   - "bus_stop": 차가 버스 정류소 표지판·승강장에서 약 10m 안에 있음
			   - "school_zone": 차가 어린이보호구역(빨간 노면, '어린이보호구역' 글자) 안에 있음
			   - "fire_lane": 차가 소방차 전용구역(빨간 테두리 칸, '소방차 전용' 글자)에 있음
			   - "sign": 주정차 금지 표지판이 차 바로 근처에 있음
			   - "none": 위에 해당하는 것이 없음

			label은 위에서부터 처음 맞는 규칙으로 골라:
			- crosswalk가 "on" → CROSSWALK
			- hydrant가 "near" → HYDRANT
			- sidewalk가 "on" → SIDEWALK
			- crosswalk가 "near"(횡단보도 10m 안)이거나 noParking이 "none"이 아님 → OTHER
			- 그 밖 → NORMAL (흰 주차구획선 안, 주차가 허용된 도로변 등)

			probabilities는 5가지 유형의 가능성(합계 1)이야.
			- 근거가 사진에 또렷하게 보이면 label에 0.8 이상
			- 근거가 일부 가려지거나 애매하면 label에 0.4~0.7
			- vehicle이 "none"이나 "unclear"면 어떤 유형도 0.3을 넘기지 마
			reason은 무엇을 보고 그렇게 판단했는지 한국어 한두 문장으로 써.

			반드시 아래 형식의 JSON 하나만 출력하고 다른 말은 하지 마:
			{"checks":{"vehicle":"parked|moving|none|unclear","crosswalk":"on|near|none","sidewalk":"on|none","hydrant":"near|far|none","noParking":"yellow_line|intersection|bus_stop|school_zone|fire_lane|sign|none"},"label":"CROSSWALK|SIDEWALK|HYDRANT|OTHER|NORMAL","probabilities":{"CROSSWALK":0~1,"SIDEWALK":0~1,"HYDRANT":0~1,"OTHER":0~1,"NORMAL":0~1},"reason":"판단 근거"}""";

	/** NVIDIA 모델용. Super VL은 횡단보도·보도를 너무 쉽게 "위"로 봐서, 차 아래를 확인하라고 엄격하게 적었어요. */
	static final String SYSTEM_FOR_NVIDIA = TEMPLATE
			.replace("{vehicle}\n",
			"   - \"parked\": 멈춰 세워 둔(주차·정차한) 자동차가 사진에 크게 보임\n" +
			"   - \"moving\": 자동차가 있지만 도로를 달리는 중으로 보임\n")
			.replace("{crosswalk}\n",
			"   - \"on\": 차 바로 아래(바퀴 사이) 바닥에 줄무늬가 있음. 차체가 줄무늬를 덮고 있어야 해.\n" +
			"   - \"near\": 줄무늬가 차 앞이나 뒤에 보이지만 차 아래에는 없음, 차가 줄무늬에서 약 10m(차 두 대 길이) 안에 있음\n")
			.replace("{sidewalk}\n",
			"   - \"on\": 바퀴가 하나라도 연석(턱) 위의 보도·보도블록에 올라가 있음 (한쪽 바퀴만 걸쳐도 on)\n" +
			"     보도는 회색 보도블록뿐 아니라 초록색·빨간색 등으로 칠한 보도, 점자블록이 깔린 길도 포함해.\n" +
			"   - \"none\": 바퀴가 모두 차도(아스팔트)에 있음. 차와 보도 사이에 울타리·볼라드가 있으면 none\n" +
			"   - 사진을 보도에서 찍어서 사진 앞쪽에 보도블록이 보이는 것만으로는 \"on\"이 아니야. 차 바퀴가 놓인 바닥을 봐.\n");

	/** Claude용. Claude는 반대로 조금만 걸친 차를 "근처"로 봐서, 일부만 걸쳐도 "위"라고 적었어요. (2026-11-14 세트 A로 조정) */
	static final String SYSTEM_FOR_CLAUDE = TEMPLATE
			.replace("{vehicle}\n",
			"   - \"parked\": 멈춰 세워 둔(주차·정차한) 자동차가 사진에 크게 보임\n" +
			"     횡단보도·보도 위나 길가에 서 있는 차는 사람이 타고 있거나 잠깐 멈춘 것처럼 보여도 parked야.\n" +
			"   - \"moving\": 자동차가 차로 한가운데를 달리는 중인 것이 분명함\n")
			.replace("{crosswalk}\n",
			"   - \"on\": 차의 일부라도 횡단보도 줄무늬 위에 있음 (바퀴 하나나 앞·뒤 범퍼만 줄무늬에 걸쳐도 on)\n" +
			"     줄무늬가 차에 가려져서 끊겨 보이거나, 차가 횡단보도 길목을 막고 서 있으면 on이야.\n" +
			"   - \"near\": 차 전체가 줄무늬 밖에 있지만, 줄무늬에서 약 10m(차 두 대 길이) 안에 있음\n")
			.replace("{sidewalk}\n",
			"   - \"on\": 바퀴가 하나라도 연석(턱) 위의 보도·보도블록에 올라가 있음 (한쪽 바퀴만 걸쳐도 on)\n" +
			"     보도는 회색 보도블록뿐 아니라 초록색·빨간색 등으로 칠한 보도, 점자블록이 깔린 길도 포함해.\n" +
			"     차가 가게·건물 앞 보도블록 위나, 사람이 걸어 다니는 길 위에 서 있으면 on이야.\n" +
			"   - \"none\": 바퀴가 모두 차도(아스팔트)에 있음. 차와 보도 사이에 울타리·볼라드가 있으면 none\n");

	/** 사진과 함께 보내는 요청 문장 (user 메시지) */
	static final String USER = "이 사진의 자동차를 위 순서대로 확인하고 JSON으로 답해 줘.";
}
