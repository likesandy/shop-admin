from pathlib import Path
import xml.etree.ElementTree as ET
p = Path(__file__).resolve().parents[1] / 'backend/admin-starter/target/site/jacoco-aggregate/jacoco.xml'
root=ET.parse(p).getroot()
gates = {
    'auth/AuthService': .70,
    'auth/Tokens': .70,
    'system/DataScope': .70,
    'system/DictService': .70,
    'system/DeptCache': .70,
    'audit/AuditAspect': .70,
    'audit/AuditWriter': .70,
    'audit/AuditDrain': .70,
    'auth/MetricsSecurityConfig': .70,
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
    'generator/CrudGenerator': .50,
    'generated/NoteController': .50,
    'generated/NoteService': .50,
    'generated/NoteEntity': .50,
    'generated/NoteInput': .50,
}
for suffix, threshold in gates.items():
    if suffix.startswith(('generator/', 'generated/')):
        module = 'admin-generator' if suffix.startswith('generator/') else 'admin-generated-example'
        report = ET.parse(p.parents[4] / module / 'target/site/jacoco/jacoco.xml').getroot()
    else:
        report = root
    cls=next(c for c in report.iter('class') if c.attrib['name'].endswith(suffix))
    count=next(c for c in cls.findall('counter') if c.attrib['type']=='LINE')
    hit,miss=int(count.attrib['covered']),int(count.attrib['missed'])
    ratio=hit/(hit+miss)
    print(f'{suffix}: {ratio:.1%}')
    if ratio < threshold: raise SystemExit(f'Coverage gate failed: {suffix} < {threshold:.0%}')
