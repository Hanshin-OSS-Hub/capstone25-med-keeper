from services.gpt_service import normalize_drug_info

sample = [
    {
        "itemName": "타이레놀정500밀리그람",
        "ingredient": None,
        "appearance": "흰색의 장방형 필름코팅정",
        "effect": "감기로 인한 발열 및 동통, 두통 등에 사용"
    }
]

result = normalize_drug_info(sample)

print(result)
