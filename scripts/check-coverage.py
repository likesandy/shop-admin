from pathlib import Path
import xml.etree.ElementTree as ET
p = Path(__file__).resolve().parents[1] / 'backend/admin-starter/target/site/jacoco-aggregate/jacoco.xml'
root=ET.parse(p).getroot()
gates = {
    'auth/AuthService': .70,
    'auth/Tokens': .70,
    'system/DataScope': .70,
    'system/UserService': .70,
    'system/UserRepository': .70,
    'system/RoleService': .70,
    'system/RoleRepository': .70,
    'system/DepartmentService': .70,
    'system/DepartmentRepository': .70,
    'system/PermissionService': .70,
    'system/PermissionRepository': .70,
    'system/AuditQueryService': .70,
    'system/AuditQueryRepository': .70,
    'system/OverviewService': .70,
    'system/OverviewRepository': .70,
    # The generator is infrastructure for generated CRUD. Keep its own
    # metadata/template pipeline covered even though generated business code
    # is reviewed in the consuming module after generation.
    'generator/CrudGenerator': .50,
}
for suffix, threshold in gates.items():
    report = ET.parse(p.parents[4] / 'admin-generator/target/site/jacoco/jacoco.xml').getroot() if suffix.startswith('generator/') else root
    cls=next(c for c in report.iter('class') if c.attrib['name'].endswith(suffix))
    count=next(c for c in cls.findall('counter') if c.attrib['type']=='LINE')
    hit,miss=int(count.attrib['covered']),int(count.attrib['missed'])
    ratio=hit/(hit+miss)
    print(f'{suffix}: {ratio:.1%}')
    if ratio < threshold: raise SystemExit(f'Coverage gate failed: {suffix} < {threshold:.0%}')
