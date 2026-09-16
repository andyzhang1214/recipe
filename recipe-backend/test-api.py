# -*- coding: utf-8 -*-
"""recipe-backend 接口链路实测（本地 8080，沙箱需 noproxy）"""
import json
import urllib.request
import urllib.error
import urllib.parse
import subprocess

BASE = 'http://localhost:8080'
PASS = 0
FAIL = 0

# 清空业务表，保证脚本可重复运行（Flyway 表结构不动）
subprocess.run(
    'mysql -uroot -proot recipe -e "SET FOREIGN_KEY_CHECKS=0; '
    'TRUNCATE TABLE favorite; TRUNCATE TABLE comment; TRUNCATE TABLE rating; '
    'TRUNCATE TABLE step; TRUNCATE TABLE ingredient; TRUNCATE TABLE recipe; '
    'TRUNCATE TABLE family_invite; TRUNCATE TABLE family_member; TRUNCATE TABLE family; '
    'TRUNCATE TABLE fridge_item; TRUNCATE TABLE banner; TRUNCATE TABLE user; '
    'SET FOREIGN_KEY_CHECKS=1;"',
    shell=True, capture_output=True, check=True)


def req(path, method='GET', data=None, token=None, raw=False):
    # 对查询串做 URL 编码（中文关键词等）
    if '?' in path:
        p, q = path.split('?', 1)
        q = urllib.parse.quote(q, safe='=&%')
        path = p + '?' + q
    url = BASE + path
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(url, data=body, method=method)
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('Authorization', 'Bearer ' + token)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            content = resp.read().decode()
            return json.loads(content) if content and not raw else content
    except urllib.error.HTTPError as e:
        content = e.read().decode()
        try:
            return json.loads(content)
        except Exception:
            return {'code': e.code, 'message': content[:200]}


def check(name, cond, extra=''):
    global PASS, FAIL
    mark = 'PASS' if cond else 'FAIL'
    if cond:
        PASS += 1
    else:
        FAIL += 1
    print(f'[{mark}] {name} {extra}')
    return cond


# 1. 登录两个用户
a = req('/api/auth/wxlogin', 'POST', {'code': 'uA1', 'nickname': 'Andy', 'avatar': ''})
check('A wxlogin', a.get('code') == 0 and a.get('data', {}).get('token'), f"-> {a.get('message')}")
tokenA = a['data']['token']
b = req('/api/auth/wxlogin', 'POST', {'code': 'uB1', 'nickname': '小李'})
check('B wxlogin', b.get('code') == 0, f"-> {b.get('message')}")
tokenB = b['data']['token']

# 2. 公开只读接口
banners = req('/api/banners')
check('banners 游客可读', banners.get('code') == 0 and banners.get('data') == [], f"len={len(banners.get('data', []))}")
cal = req('/api/food-calories?keyword=鸡蛋')
check('food-calories 搜索鸡蛋', cal.get('code') == 0 and len(cal.get('data', [])) >= 1,
      f"-> {[(x['name'], x['caloriesPer100']) for x in cal.get('data', [])]}")
pub = req('/api/recipes/public')
check('recipes/public 可读', pub.get('code') == 0 and isinstance(pub.get('data', {}).get('content'), list))

# 3. 未登录访问受限接口应 401
noauth = req('/api/recipes/mine')
check('未登录访问 mine 拒绝', noauth.get('code') == 401, f"-> {noauth.get('message')}")

# 4. A 发布公开菜谱
create = req('/api/recipes', 'POST', {
    'title': '红烧排骨',
    'cover': 'https://example.com/covers/pork.jpg',
    'visibility': 'PUBLIC',
    'difficulty': '中等',
    'servings': 3,
    'status': 'PUBLISHED',
    'ingredients': [{'name': '排骨', 'amount': '500', 'unit': 'g'},
                    {'name': '冰糖', 'amount': '20', 'unit': 'g'}],
    'steps': [{'text': '排骨焯水', 'durationSec': 600},
              {'text': '炒糖色后加水炖 30 分钟', 'durationSec': 1800}]
}, tokenA)
rid = create.get('data')
check('A 发布菜谱', create.get('code') == 0 and rid, f"id={rid}")

# 5. 详情：登录可看；游客看详情触发登录(401，符合「点详情需登录」需求)
guest_detail = req(f'/api/recipes/{rid}')
check('游客看详情触发登录', guest_detail.get('code') == 401, f"-> {guest_detail.get('message')}")
detail = req(f'/api/recipes/{rid}', token=tokenA)
check('登录看详情', detail.get('code') == 0 and detail['data']['recipe']['title'] == '红烧排骨',
      f"ingredients={len(detail['data']['ingredients'])}, steps={len(detail['data']['steps'])}")

# 6. 评星 + 评论
r1 = req(f'/api/recipes/{rid}/rating', 'POST', {'stars': 5}, tokenA)
check('A 评 5 星', r1.get('code') == 0, f"-> {r1.get('message')}")
r2 = req(f'/api/recipes/{rid}/rating', 'POST', {'stars': 4}, tokenB)
check('B 评 4 星', r2.get('code') == 0)
detail2 = req(f'/api/recipes/{rid}', token=tokenA)
check('评分聚合更新', float(detail2['data']['recipe']['avgStars']) == 4.5 and detail2['data']['recipe']['ratingCount'] == 2,
      f"avg={detail2['data']['recipe']['avgStars']}, n={detail2['data']['recipe']['ratingCount']}")
c1 = req(f'/api/recipes/{rid}/comments', 'POST', {'content': '色香味俱全！'}, tokenB)
check('B 评论', c1.get('code') == 0, f"-> {c1.get('message')}")
c2 = req(f'/api/recipes/{rid}/comments', 'POST', {'content': '回复：谢谢！', 'parentId': c1.get('data')}, tokenA)
check('A 楼中楼回复', c2.get('code') == 0)
comments = req(f'/api/recipes/{rid}/comments', token=tokenA)
check('评论列表含回复', len(comments.get('data', [])) == 2, f"n={len(comments.get('data', []))}")

# 7. A 创建家庭圈层（默认需审批）
fam = req('/api/family', 'POST', {'name': '相亲相爱的一家', 'type': 'FAMILY'}, tokenA)
fid = fam.get('data')
check('A 创建家庭', fam.get('code') == 0 and fid, f"id={fid}")
fdetail = req(f'/api/family/{fid}', token=tokenA)
join_code = fdetail['data']['family']['joinCode']
check('圈层默认审批开', fdetail['data']['family']['approvalRequired'] is True, f"joinCode={join_code}")

# 8. 生成邀请（7天）
inv = req(f'/api/family/{fid}/invites', 'POST', {'type': 'QR_CODE', 'validDays': 7}, tokenA)
invite_token = inv.get('data', {}).get('token')
check('生成邀请', inv.get('code') == 0 and invite_token, f"token={invite_token}")

# 9. B 凭邀请加入（默认审批 → PENDING）
join = req('/api/family/join-by-invite', 'POST', {'invite': invite_token}, tokenB)
check('B 邀请加入(待审)', join.get('code') == 0, f"-> {join.get('message')}")
members = req(f'/api/family/{fid}/members', token=tokenA)
pending = [m for m in members['data'] if m['status'] == 'PENDING']
check('B 为待审成员', len(pending) == 1, f"pending={len(pending)}")

# 10. A 审批通过
mid = pending[0]['memberId']
appr = req(f'/api/family/{fid}/members/{mid}/approve?pass=true', 'POST', token=tokenA)
check('A 审批通过', appr.get('code') == 0)
members2 = req(f'/api/family/{fid}/members', token=tokenA)
check('成员数=2', len(members2['data']) == 2 and members2['data'][1]['status'] == 'APPROVED')

# 11. 过期邀请校验：造一个过期邀请（直接 SQL 改 expires_at）
req(f'/api/family/{fid}/invites', 'POST', {'type': 'SHARE_CARD', 'validDays': 1}, tokenA)
# 模拟 B 已有成员身份后重复加入应提示
dup = req('/api/family/join-by-invite', 'POST', {'invite': invite_token}, tokenB)
check('重复加入被拒', dup.get('code') != 0, f"-> {dup.get('message')}")

# 12. A 发一条仅圈层可见菜谱，B 可看、C(外人) 不可看
priv = req('/api/recipes', 'POST', {
    'title': '家传秘制红烧肉', 'visibility': 'FAMILY', 'familyId': fid,
    'status': 'PUBLISHED',
    'ingredients': [{'name': '五花肉', 'amount': '600', 'unit': 'g'}],
    'steps': [{'text': '慢炖 1 小时'}]
}, tokenA)
prid = priv.get('data')
check('A 发圈层菜谱', priv.get('code') == 0, f"id={prid}")
v1 = req(f'/api/recipes/{prid}', token=tokenB)
check('B(成员)可看圈层菜谱', v1.get('code') == 0, f"-> {v1.get('message')}")
c = req('/api/auth/wxlogin', 'POST', {'code': 'uC1', 'nickname': '外人'})
tokenC = c['data']['token']
v2 = req(f'/api/recipes/{prid}', token=tokenC)
check('C(外人)看圈层菜谱被拒', v2.get('code') == 403, f"-> {v2.get('message')}")

# 13. 冰箱录入 + 临期排序
f1 = req('/api/fridge-items', 'POST', {
    'name': '鲜牛奶', 'category': '乳品', 'quantity': 2, 'unit': '盒',
    'addedAt': '2026-08-18', 'expiryDate': '2026-08-20', 'reminderDays': 3, 'note': '永辉'
}, tokenA)
check('冰箱录入', f1.get('code') == 0, f"-> {f1.get('message')}")
f2 = req('/api/fridge-items', 'POST', {
    'name': '鸡蛋', 'category': '其他', 'addedAt': '2026-08-18',
    'expiryDate': '2026-09-01', 'reminderDays': 3
}, tokenA)
check('冰箱录入2', f2.get('code') == 0)
flist = req('/api/fridge-items?sort=status', token=tokenA)
names = [x['name'] for x in flist.get('data', [])]
check('临期排序(牛奶在前)', names == ['鲜牛奶', '鸡蛋'], f"order={names}")

# 14. 非成员不能看冰箱（B 看不到 A 的）
flist_b = req('/api/fridge-items', token=tokenB)
check('冰箱数据隔离', flist_b.get('data', []) == [], f"B len={len(flist_b.get('data', []))}")

print(f'\n===== 结果: PASS={PASS} FAIL={FAIL} =====')
exit(1 if FAIL else 0)
