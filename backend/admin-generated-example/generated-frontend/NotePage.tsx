import {useEffect,useState} from 'react';
import {Button,Card,Form,Input,InputNumber,Modal,Popconfirm,Space,Switch,Table,message} from 'antd';

// Move this page into the app, export OpenAPI, then use the generated typed client.
type Row = {id:number;title?:string|number|boolean;content?:string|number|boolean;ownerId?:string|number|boolean};
const endpoint='/api/generated/notes';
export default function NotePage(){
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
  return <Card title="Note" extra={<Space><Button onClick={()=>void reload()}>刷新</Button><Button type="primary" onClick={()=>open(null)}>新增</Button></Space>}>
    <Table rowKey="id" dataSource={rows} columns={[
      {title:'id',dataIndex:'id'},
      {title:'title',dataIndex:'title'},
      {title:'content',dataIndex:'content'},
      {title:'owner_id',dataIndex:'ownerId'},
      {title:'操作',render:(_:unknown,row:Row)=><Space><Button onClick={()=>open(row)}>编辑</Button><Popconfirm title="确定删除？" onConfirm={()=>void remove(row.id)}><Button danger>删除</Button></Popconfirm></Space>}
    ]} pagination={{pageSize:20}}/>
    <Modal title={editing?'编辑Note':'新增Note'} open={editing!==undefined} onCancel={()=>setEditing(undefined)} onOk={()=>void save()} destroyOnClose>
      <Form form={form} layout="vertical">
        <Form.Item name="title" label="title">
          <Input/>
        </Form.Item>
<Form.Item name="content" label="content">
          <Input/>
        </Form.Item>
      </Form>
    </Modal>
  </Card>;
}
