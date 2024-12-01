import numpy as np
import cv2
from matplotlib import pyplot as plt
from scipy.signal import find_peaks, savgol_filter
import time

class BreathingRateDetector:
    def __init__(self):
        self.x1, self.x2 = 0.4, 0.6
        self.y1, self.y2 = 0.1, 0.25
        self.face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
        self.gsums = []
        self.fps = 10
        self.idf = 0
        self.previous_frame = None

    def get_face_roi(self, img):
        gray = cv2.cvtColor(img, cv2.COLOR_RGB2GRAY)
        faces = self.face_cascade.detectMultiScale(gray, 1.2, 5)

        if len(faces) > 0:
            x, y, w, h = faces[0]
            roi_x1 = x + int(self.x1 * w)
            roi_y1 = y + int(self.y1 * h)
            roi_x2 = x + int(self.x2 * w)
            roi_y2 = y + int(self.y2 * h)

            cv2.rectangle(img, (roi_x1, roi_y1), (roi_x2, roi_y2), (255, 0, 0), 2)
            return [roi_x1, roi_y1, roi_x2, roi_y2]
        return [0, 0, 0, 0]

    def get_green_average(self, frame, bbox):
        roi = frame[bbox[1]:bbox[3], bbox[0]:bbox[2]]
        return sum(sum(roi[:,:,1])) / (roi.shape[0] * roi.shape[1])

    def simple_gaussian_filter(self, data, sigma=3):
        # 簡單的高斯平滑實現
        filtered = []
        for i in range(len(data)):
            window = data[max(0, i-sigma):min(len(data), i+sigma+1)]
            filtered.append(sum(window) / len(window))
        return filtered

    def find_peaks(self, data, distance=15, height=None):
        peaks = []
        if height is None:
            height = sum(data) / len(data)

        for i in range(1, len(data)-1):
            if (data[i] > data[i-1] and
                    data[i] > data[i+1] and
                    data[i] > height):
                peaks.append(i)

        return peaks

    def process_frame(frame_data, width, height):
        # 首次檢測人臉
        if self.idf == 0:
            bbox = self.get_face_roi(frame)
            if bbox[3] > 0:
                self.bbox = bbox

        # 後續幀使用運動檢測更新人臉區域
        if self.idf > 0 and self.previous_frame is not None:
            diff = cv2.absdiff(frame, self.previous_frame)
            m_diff = sum(sum(diff)) / (diff.shape[0] * diff.shape[1])

            if m_diff > 15:
                new_bbox = self.get_face_roi(frame)
                if new_bbox[3] > 0:
                    self.bbox = new_bbox

        # 計算綠色通道平均值
        green_avg = self.get_green_average(frame, self.bbox)

        # 忽略初始幀
        if self.idf > 50:
            self.gsums.append(green_avg)

        self.previous_frame = frame.copy()
        self.idf += 1

        frame = np.frombuffer(frame_data, dtype=np.uint8).reshape((height, width, 3))
        return detector.process_frame(frame)

    def calculate_breathing_rate(self):
        if len(self.gsums) < 60:  # 確保有足夠數據
            return 0

        # 數據平滑
        smoothed_gsums = self.simple_gaussian_filter(self.gsums)

        # 波峰檢測
        peaks = self.find_peaks(smoothed_gsums)

        # 計算呼吸率
        if len(peaks) > 1:
            peak_intervals = [peaks[i+1] - peaks[i] for i in range(len(peaks)-1)]
            avg_period = sum(peak_intervals) / len(peak_intervals) / self.fps
            breathing_rate = 60 / avg_period
            return breathing_rate

        return 0

# 創建全局實例
detector = BreathingRateDetector()

def process_frame(frame_data):
    """接收 numpy 圖像數據並處理"""
    frame = frame_data.reshape((height, width, 3))
    return detector.process_frame(frame)

def get_breathing_rate():
    """計算並返回呼吸率"""
    return detector.calculate_breathing_rate()