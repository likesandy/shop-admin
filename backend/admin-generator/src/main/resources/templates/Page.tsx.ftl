import {useEffect,useState} from 'react';
import {Button,Card,Form,Input,InputNumber,Modal,Popconfirm,Space,Switch,Table,message} from 'antd';

// Move this page into the app, export OpenAPI, then use the generated typed client.
type Row = {id:number<#list columns as column><#if column.column != "id">;${column.field}?:string|number|boolean</#if></#list>};
const endpoint='/api/generated/${resource}';
export default function ${className}Page(){
  const [rows,setRows]=useState<Row[]>([]);
  const [editing,setEditing]=useState<Row|null>();
  const [form]=Form.useForm();
  const request=async(path:string,method='GET',body?:unknown)=>{
    const response=await fetch(endpoint+path,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+(sessionStorage.getItem('shop-token')||'')},body:body===undefined?undefined:JSON.stringify(body)});
    const result=await response.json();
    if(!response.ok)throw new Error(result.message||'请求失败');
    return result.data;
  };
  const reload=async()=>{try{setRows(await request(''))}catch(error){message.error((error as Error).message)}};
  useEffect(()=>{void reload()},[]);
  const open=(row:Row|null)=>{form.resetFields();form.setFieldsValue(row||{});setEditing(row)};
  const save=async()=>{try{const body=await form.validateFields();await request(editing?'/'+editing.id:'',editing?'PUT':'POST',body);setEditing(undefined);await reload();message.success('保存成功')}catch(error){message.error((error as Error).message)}};
  const remove=async(id:number)=>{try{await request('/'+id,'DELETE');await reload();message.success('删除成功')}catch(error){message.error((error as Error).message)}};
  return <Card title="${className}" extra={<Space><Button onClick={()=>void reload()}>刷新</Button><Button type="primary" onClick={()=>open(null)}>新增</Button></Space>}>
    <Table rowKey="id" dataSource={rows} columns={[
      <#list columns as column>{title:'${column.column}',dataIndex:'${column.field}'},
      </#list>{title:'操作',render:(_:unknown,row:Row)=><Space><Button onClick={()=>open(row)}>编辑</Button><Popconfirm title="确定删除？" onConfirm={()=>void remove(row.id)}><Button danger>删除</Button></Popconfirm></Space>}
    ]} pagination={{pageSize:20}}/>
    <Modal title={editing?'编辑${className}':'新增${className}'} open={editing!==undefined} onCancel={()=>setEditing(undefined)} onOk={()=>void save()} destroyOnClose>
      <Form form={form} layout="vertical">
        <#list columns as column><#if column.column != "id"><Form.Item name="${column.field}" label="${column.column}"<#if column.type == "Boolean"> valuePropName="checked"</#if>>
          <#if column.type == "Boolean"><Switch/><#elseif column.type == "Integer" || column.type == "Long" || column.type == "java.math.BigDecimal"><InputNumber style={{width:'100%'}}/><#else><Input/></#if>
        </Form.Item>
        </#if></#list>
      </Form>
    </Modal>
  </Card>;
}
