#!/usr/bin/env python3
"""
简易Mock服务器，用于测试BlindAssist客户端
支持所有interface.md中定义的API端点
"""

import json
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse
import threading
import websocket
from websocket_server import WebsocketServer
import base64

class MockRequestHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/api/voice/command':
            self.handle_voice_command()
        elif self.path.startswith('/api/vision/frame'):
            self.handle_vision_frame()
        elif self.path == '/api/vision/frames':
            self.handle_vision_frames()
        elif self.path == '/api/control/sdk-result':
            self.handle_sdk_result()
        elif self.path == '/api/qa/ask':
            self.handle_qa_ask()
        elif self.path == '/api/navigation/route':
            self.handle_navigation_route()
        else:
            self.send_error(404, "Not Found")

    def do_GET(self):
        # 简单的健康检查
        self.send_response(200)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps({"status": "ok", "message": "Mock server running"}).encode())

    def handle_voice_command(self):
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        request_data = json.loads(post_data.decode('utf-8'))

        text = request_data.get('text', '')

        # 简单的意图分类逻辑
        if any(word in text for word in ['导航', '去', '路线', '怎么走']):
            feature = 'NAVIGATION'
            detail = f'导航到{text}'
            prompt = f'用户需要导航到{text}，请提供详细路线指引'
        elif any(word in text for word in ['文字', '识别', 'OCR', '读一下']):
            feature = 'OCR'
            detail = '文字识别请求'
            prompt = '请对用户提供的图像进行OCR文字识别'
        elif any(word in text for word in ['场景', '环境', '描述', '看到什么']):
            feature = 'SCENE_DESCRIPTION'
            detail = '场景描述请求'
            prompt = '请描述用户提供的图像中的场景内容'
        elif any(word in text for word in ['避障', '障碍', '注意', '小心']):
            feature = 'OBSTACLE_AVOIDANCE'
            detail = '避障模式启动'
            prompt = '启动实时避障模式，持续分析视频流'
        elif any(word in text for word in ['控制', '操作', '点击', '打开']):
            feature = 'CONTROL_SDK'
            detail = 'AutoGLM控制指令'
            prompt = '执行AutoGLM自动化控制指令'
        else:
            feature = 'QA_VOICE'
            detail = text
            prompt = f'回答用户的问题：{text}'

        response = {
            'feature': feature,
            'detail': detail,
            'prompt': prompt
        }

        self.send_json_response(response)

    def handle_vision_frame(self):
        # 读取二进制图像数据
        content_length = int(self.headers['Content-Length'])
        image_data = self.rfile.read(content_length)

        # 解析查询参数
        query_params = parse_qs(urlparse(self.path).query)
        scene_type = query_params.get('sceneType', ['general'])[0]
        frame_seq = query_params.get('frameSeq', ['1'])[0]

        print(f"Received vision frame: sceneType={scene_type}, frameSeq={frame_seq}, size={len(image_data)} bytes")

        response = {
            'status': 'ok',
            'frameId': f'frame-{int(time.time()*1000)}',
            'message': 'frame received'
        }

        self.send_json_response(response)

    def handle_vision_frames(self):
        # 简化处理，实际multipart需要更复杂的解析
        response = {
            'status': 'ok',
            'frameIds': [f'batch-{int(time.time()*1000)}'],
            'message': 'frames received (mock)'
        }
        self.send_json_response(response)

    def handle_sdk_result(self):
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        request_data = json.loads(post_data.decode('utf-8'))

        print(f"SDK Execution Result: {request_data}")

        self.send_json_response({'status': 'ok', 'message': 'result recorded'})

    def handle_qa_ask(self):
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        # 简单问答响应
        response = {
            'answer': '这是模拟的回答。您的问题已收到并处理。',
            'sessionId': 'mock-session'
        }
        self.send_json_response(response)

    def handle_navigation_route(self):
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        # 模拟导航路线
        response = {
            'voiceSteps': [
                '第一步：向前直行100米',
                '第二步：在路口右转',
                '第三步：目的地就在您的左手边'
            ]
        }
        self.send_json_response(response)

    def send_json_response(self, data, status_code=200):
        self.send_response(status_code)
        self.send_header('Content-type', 'application/json; charset=utf-8')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode('utf-8'))

    def do_OPTIONS(self):
        # 处理CORS预检请求
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

def start_http_server(port=8080):
    server = HTTPServer(('0.0.0.0', port), MockRequestHandler)
    print(f"Mock HTTP server running on port {port}")
    server.serve_forever()

def new_client(client, server):
    print(f"New WebSocket client connected: {client['id']}")

def client_left(client, server):
    print(f"WebSocket client disconnected: {client['id']}")

def message_received(client, server, message):
    try:
        data = json.loads(message)
        msg_type = data.get('type')

        if msg_type == 'init':
            # 初始化消息
            scene_type = data.get('sceneType', 'general')
            session_id = data.get('sessionId', 'mock')
            print(f"Vision stream initialized: sceneType={scene_type}, sessionId={session_id}")

            # 发送确认消息
            response = {
                'type': 'ack',
                'message': 'vision stream connected'
            }
            server.send_message(client, json.dumps(response))

            # 模拟发送一些测试指令
            if scene_type == 'ocr':
                test_msg = {
                    'type': 'ocr_result',
                    'data': {'text': '模拟OCR识别结果：前方有盲道，请沿盲道直行'}
                }
            elif scene_type == 'scene_description':
                test_msg = {
                    'type': 'scene_description_result',
                    'data': {'description': '模拟场景描述：您正站在十字路口，前方是红灯'}
                }
            else:
                test_msg = {
                    'type': 'obstacle_warning',
                    'data': {'message': '模拟避障提示：注意，前方有台阶'}
                }

            # 延迟发送测试消息
            def send_test():
                time.sleep(2)
                server.send_message(client, json.dumps(test_msg))

            threading.Thread(target=send_test).start()

        elif msg_type == 'control':
            # AutoGLM控制指令
            print(f"Received AutoGLM control command: {data}")

            # 模拟执行结果
            action = data.get('data', {}).get('action', 'unknown')
            session_id = data.get('sessionId', 'mock')
            task_id = data.get('taskId', 'mock-task')

            # 发送执行结果确认
            result_msg = {
                'type': 'execution_result',
                'sessionId': session_id,
                'taskId': task_id,
                'success': True,
                'action': action
            }
            server.send_message(client, json.dumps(result_msg))

    except json.JSONDecodeError:
        print(f"Received non-JSON message: {message}")

def start_websocket_server(port=8081):
    server = WebsocketServer(host='0.0.0.0', port=port)
    server.set_fn_new_client(new_client)
    server.set_fn_client_left(client_left)
    server.set_fn_message_received(message_received)
    print(f"Mock WebSocket server running on port {port}")
    server.run_forever()

if __name__ == '__main__':
    # 启动HTTP服务器（端口8080）
    http_thread = threading.Thread(target=start_http_server, args=(8080,))
    http_thread.daemon = True
    http_thread.start()

    # 启动WebSocket服务器（端口8081）
    ws_thread = threading.Thread(target=start_websocket_server, args=(8081,))
    ws_thread.daemon = True
    ws_thread.start()

    print("Mock servers started!")
    print("HTTP API: http://localhost:8080")
    print("WebSocket: ws://localhost:8081")
    print("Press Ctrl+C to stop")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nShutting down mock servers...")