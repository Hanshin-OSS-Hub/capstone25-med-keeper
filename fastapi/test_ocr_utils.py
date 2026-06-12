from services.ocr_services import filter_korean, get_chosung, similarity 

def test_filter_korean():
    assert filter_korean("타이레놀 500mg!") == "타이레놀500"

def test_get_chosung():
    assert get_chosung("타이레놀") == "ㅌㅇㄹㄴ"

def test_similarity_same_text():
    assert similarity("타이레놀", "타이레놀") == 1
