import json
from pathlib import Path

json_path = Path("../export.json")
with open(json_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

first = data[0]
results = first['annotations'][0]['result']

print("모든 result 상세:")
for idx, r in enumerate(results):
    print(f"\n[{idx}] type: {r['type']}")
    print(f"    전체 keys: {list(r.keys())}")
    if 'id' in r:
        print(f"    id: {r['id']}")
    if 'from_id' in r:
        print(f"    from_id: {r['from_id']}")
    if 'to_id' in r:
        print(f"    to_id: {r['to_id']}")
    if 'parent_id' in r:
        print(f"    parent_id: {r['parent_id']}")