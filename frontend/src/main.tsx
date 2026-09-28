import React,{Suspense,lazy} from 'react';import ReactDOM from 'react-dom/client';import {ConfigProvider,Spin} from 'antd';import zhCN from 'antd/locale/zh_CN';import './style.css';
const App=lazy(()=>import('./App'));
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><ConfigProvider locale={zhCN} theme={{token:{colorPrimary:'#247b73',borderRadius:8,fontFamily:'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'}}}><Suspense fallback={<div className="loading"><Spin size="large"/></div>}><App/></Suspense></ConfigProvider></React.StrictMode>);
