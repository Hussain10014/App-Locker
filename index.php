<?php
/**
 * ZenLock - Phone Locker PHP Controller / Alternative Server
 * Run locally via: php -S 0.0.0.0:8000
 */

$requestUri = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$method = $_SERVER['REQUEST_METHOD'];
$logFile = __DIR__ . '/focus_sessions.json';

// Handle API session logging
if ($requestUri === '/api/session' || (isset($_GET['api']) && $_GET['api'] === 'session')) {
    header('Content-Type: application/json');
    header('Access-Control-Allow-Origin: *');

    if ($method === 'POST') {
        $rawInput = file_get_contents('php://input');
        $data = json_decode($rawInput, true);

        if ($data) {
            $data['server_received_at'] = date('Y-m-d H:i:s');
            
            $existing = [];
            if (file_exists($logFile)) {
                $content = file_get_contents($logFile);
                $existing = json_decode($content, true) ?: [];
            }
            $existing[] = $data;
            file_put_contents($logFile, json_encode($existing, JSON_PRETTY_PRINT));

            echo json_encode(['status' => 'success', 'message' => 'Session logged']);
            exit;
        } else {
            http_response_code(400);
            echo json_encode(['status' => 'error', 'message' => 'Invalid JSON']);
            exit;
        }
    }
}

// If using PHP built-in server to serve static assets directly
$requestedFile = __DIR__ . $requestUri;
if ($requestUri !== '/' && file_exists($requestedFile) && !is_dir($requestedFile)) {
    // Deliver static file
    return false;
}

// Otherwise deliver index.html
include __DIR__ . '/index.html';
